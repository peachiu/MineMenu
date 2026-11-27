package com.minexray.scan;

import com.minexray.BlockFilter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/**
 * Finds the blocks MineXray shows, off the main thread. On the client thread, each section first gets a
 * <b>palette check</b> ({@link PalettedContainer#hasAny}): a section whose palette holds nothing we look for
 * (most of them) is marked empty without copying or scanning. Only the others are copied
 * ({@link PalettedContainer#copy()}) and the copy is scanned by one low-priority background thread that never
 * touches the world. Near sections, and the ones in front of you, go first.
 */
public final class SectionScanner {
    /** What to look for; null when nothing that needs a scan is on. */
    public record Needs(boolean esp, boolean lava, boolean water, boolean cave, int caveMaxY, int radius) {
        int signature(int filterEpoch) {
            return java.util.Objects.hash(esp, lava, water, cave, caveMaxY, filterEpoch);
        }
    }

    private static final int MAX_SUBMITS_PER_TICK = 48;
    private static final int MAX_PENDING = 256;
    static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MineXray scanner");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private static final Set<Long> pending = ConcurrentHashMap.newKeySet();
    private static final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    private static volatile Needs needs;
    private static int signature;
    private static int filterEpoch;
    /** Section offsets within the radius, nearest first: packed (dx, dy, dz) + 64 each, 8 bits apiece. */
    private static int[] offsets = new int[0];
    private static int offsetsRadius = -1;
    private static long lastCenter = Long.MIN_VALUE;
    private static boolean complete;
    private static int ticks;
    /** For tests and the Overview page. */
    public static volatile int scannedSections;
    public static volatile int skippedByPalette;

    private SectionScanner() {
    }

    /** Throws every result away (the block list or what we look for changed). */
    public static void invalidateAll() {
        filterEpoch++;
    }

    /** A block changed: scan its section again (if we scanned it before). */
    public static void markDirty(BlockPos pos) {
        if (needs == null) {
            return;
        }
        long key = ChunkSectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        if (ScanCache.has(key)) {
            dirty.add(key);
            complete = false;
        }
    }

    public static void chunkChanged(int chunkX, int chunkZ) {
        ScanCache.forgetChunk(chunkX, chunkZ);
        complete = false;
    }

    public static void clear() {
        ScanCache.clear();
        pending.clear();
        dirty.clear();
        complete = false;
        lastCenter = Long.MIN_VALUE;
    }

    public static boolean idle() {
        return pending.isEmpty() && dirty.isEmpty() && complete;
    }

    public static int pending() {
        return pending.size();
    }

    /** Client thread, every tick. {@code n} null: nothing needs scanning, so the cache is freed. */
    public static void tick(MinecraftClient client, Needs n, long memoryLimitBytes) {
        ClientWorld world = client.world;
        if (n == null || world == null || client.gameRenderer == null) {
            if (needs != null || ScanCache.size() > 0) {
                needs = null;
                clear();
            }
            return;
        }
        int sig = n.signature(filterEpoch);
        if (needs == null || sig != signature) {
            signature = sig;
            clear();
        }
        needs = n;
        Mesher.tick();
        BlockPos camera = client.gameRenderer.getCamera().getBlockPos();
        int cx = camera.getX() >> 4;
        int cy = camera.getY() >> 4;
        int cz = camera.getZ() >> 4;
        long center = ChunkSectionPos.asLong(cx, cy, cz);
        int rs = (n.radius() + 15) >> 4;
        if (center != lastCenter || rs != offsetsRadius) {
            lastCenter = center;
            complete = false;
        }
        if (++ticks % 40 == 0) {
            ScanCache.trim(cx, cy, cz, rs + 2, memoryLimitBytes);
        }
        if (complete || pending.size() >= MAX_PENDING) {
            return;
        }
        if (rs != offsetsRadius) {
            offsets = sortedOffsets(rs);
            offsetsRadius = rs;
        }
        int budget = MAX_SUBMITS_PER_TICK;
        // Changed blocks first: they're right where you're looking.
        for (Iterator<Long> it = dirty.iterator(); it.hasNext() && budget > 0; ) {
            long key = it.next();
            it.remove();
            if (submit(world, key, n)) {
                budget--;
            }
        }
        Vec3d look = Vec3d.fromPolar(client.gameRenderer.getCamera().getPitch(), client.gameRenderer.getCamera().getYaw());
        List<Long> behind = new ArrayList<>();
        boolean missing = false;
        for (int packed : offsets) {
            if (budget <= 0) {
                missing = true;
                break;
            }
            int dx = (packed >> 16 & 0xFF) - 64;
            int dy = (packed >> 8 & 0xFF) - 64;
            int dz = (packed & 0xFF) - 64;
            int sy = cy + dy;
            if (sy < world.getBottomSectionCoord() || sy >= world.getTopSectionCoord()) {
                continue;
            }
            long key = ChunkSectionPos.asLong(cx + dx, sy, cz + dz);
            if (ScanCache.has(key) || pending.contains(key)) {
                continue;
            }
            if (!world.getChunkManager().isChunkLoaded(cx + dx, cz + dz)) {
                continue;
            }
            missing = true;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 1.5 && (dx * look.x + dy * look.y + dz * look.z) / length < 0.2) {
                behind.add(key); // behind you: after the ones in view
                continue;
            }
            if (submit(world, key, n)) {
                budget--;
            }
        }
        for (int i = 0; i < behind.size() && budget > 0; i++) {
            if (submit(world, behind.get(i), n)) {
                budget--;
            }
        }
        complete = !missing && pending.isEmpty();
    }

    /** Palette check, then copy and queue. Returns true if it cost a copy. */
    private static boolean submit(ClientWorld world, long key, Needs n) {
        int sx = ChunkSectionPos.unpackX(key);
        int sy = ChunkSectionPos.unpackY(key);
        int sz = ChunkSectionPos.unpackZ(key);
        if (!world.getChunkManager().isChunkLoaded(sx, sz)) {
            return false;
        }
        WorldChunk chunk = world.getChunk(sx, sz);
        int index = world.sectionCoordToIndex(sy);
        if (index < 0 || index >= chunk.getSectionArray().length) {
            return false;
        }
        ChunkSection section = chunk.getSection(index);
        int gen = ScanCache.generation();
        BlockFilter filter = BlockFilter.get();
        boolean cave = n.cave() && (sy << 4) <= n.caveMaxY();
        if (section.isEmpty()) {
            ScanCache.put(SectionData.empty(key, signature), gen);
            return false;
        }
        PalettedContainer<BlockState> container = section.getBlockStateContainer();
        Predicate<BlockState> wanted = state -> (n.esp() && filter.isScanned(state))
                || (n.lava() && state.isOf(Blocks.LAVA)) || (n.water() && state.isOf(Blocks.WATER))
                || (cave && state.isOf(Blocks.CAVE_AIR));
        if (!container.hasAny(wanted)) {
            skippedByPalette++;
            ScanCache.put(SectionData.empty(key, signature), gen);
            Mesher.neighbourChanged(key);
            return false;
        }
        PalettedContainer<BlockState> copy = container.copy();
        int sig = signature;
        pending.add(key);
        EXECUTOR.execute(() -> {
            try {
                SectionData data = scan(key, copy, n, filter, cave, sig);
                ScanCache.put(data, gen);
                scannedSections++;
                Mesher.build(data);
                Mesher.neighbourChanged(key);
            } catch (Throwable t) {
                com.minexray.MineXrayClient.backgroundError("scanner", t);
            } finally {
                pending.remove(key);
            }
        });
        return true;
    }

    /** Background thread: reads only the copy. */
    private static SectionData scan(long key, PalettedContainer<BlockState> copy, Needs n, BlockFilter filter, boolean cave, int sig) {
        short[] idx = null;
        int[] states = null;
        int count = 0;
        long[] lava = null;
        long[] water = null;
        long[] caveBits = null;
        int caveCount = 0;
        int baseY = ChunkSectionPos.unpackY(key) << 4;
        for (int i = 0; i < 4096; i++) {
            BlockState state = copy.get(i & 15, i >> 8, (i >> 4) & 15);
            if (state.isAir() && !(cave && state.isOf(Blocks.CAVE_AIR))) {
                continue;
            }
            int raw = BlockFilter.raw(state);
            if (n.esp() && filter.isScanned(raw)) {
                if (idx == null) {
                    idx = new short[32];
                    states = new int[32];
                } else if (count == idx.length) {
                    idx = java.util.Arrays.copyOf(idx, count * 2);
                    states = java.util.Arrays.copyOf(states, count * 2);
                }
                idx[count] = (short) i;
                states[count++] = raw;
            } else if (n.lava() && state.isOf(Blocks.LAVA)) {
                if (lava == null) {
                    lava = new long[64];
                }
                SectionData.set(lava, i);
            } else if (n.water() && state.isOf(Blocks.WATER)) {
                if (water == null) {
                    water = new long[64];
                }
                SectionData.set(water, i);
            } else if (cave && state.isOf(Blocks.CAVE_AIR) && baseY + (i >> 8) <= n.caveMaxY()) {
                if (caveBits == null) {
                    caveBits = new long[64];
                }
                SectionData.set(caveBits, i);
                caveCount++;
            }
        }
        return new SectionData(key, idx == null ? SectionData.NO_BLOCKS : java.util.Arrays.copyOf(idx, count),
                states == null ? SectionData.NO_STATES : java.util.Arrays.copyOf(states, count), lava, water, caveBits, caveCount, sig);
    }

    private static int[] sortedOffsets(int rs) {
        List<int[]> list = new ArrayList<>();
        int limit = (rs + 1) * (rs + 1);
        for (int dx = -rs; dx <= rs; dx++) {
            for (int dy = -rs; dy <= rs; dy++) {
                for (int dz = -rs; dz <= rs; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 <= limit) {
                        list.add(new int[]{d2, dx, dy, dz});
                    }
                }
            }
        }
        list.sort((a, b) -> Integer.compare(a[0], b[0]));
        int[] result = new int[list.size()];
        for (int i = 0; i < result.length; i++) {
            int[] o = list.get(i);
            result[i] = (o[1] + 64) << 16 | (o[2] + 64) << 8 | (o[3] + 64);
        }
        return result;
    }

    public static Needs needs() {
        return needs;
    }
}
