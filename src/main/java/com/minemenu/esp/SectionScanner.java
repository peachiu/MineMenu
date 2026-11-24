package com.minemenu.esp;

import com.minemenu.OreColors;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ore Vision's block scan, off the main thread. Concurrency: the client thread takes a <b>copy</b> of each
 * 16x16x16 chunk section ({@link PalettedContainer#copy()}, a few array copies) and hands only that copy to one
 * background thread, which never touches the world. Results go into a {@link ConcurrentHashMap} as new
 * immutable arrays, which the render thread reads without locks. A section is scanned once and again only when
 * a block in it changes (see ClientBlockUpdateMixin) or its chunk reloads, never every frame.
 */
public final class SectionScanner {
    private static final long[] EMPTY = new long[0];
    private static final int MAX_SUBMITS_PER_TICK = 24;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MineMenu ESP scanner");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    /** Section key -> pairs of (block pos as long, colour). */
    private static final Map<Long, long[]> results = new ConcurrentHashMap<>();
    private static final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    private static final Set<Long> pending = ConcurrentHashMap.newKeySet();
    /** Bumped on clear(): results of scans started before it are thrown away. */
    private static volatile int generation;

    private SectionScanner() {
    }

    public static void markDirty(BlockPos pos) {
        if (!results.isEmpty()) {
            dirty.add(ChunkSectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        }
    }

    public static void forgetChunk(int chunkX, int chunkZ) {
        results.keySet().removeIf(key -> ChunkSectionPos.unpackX(key) == chunkX && ChunkSectionPos.unpackZ(key) == chunkZ);
    }

    public static void clear() {
        generation++;
        results.clear();
        dirty.clear();
        pending.clear();
    }

    /** Client thread: queue scans for sections around the camera that are new or changed. */
    public static void tick(MinecraftClient client, int radius) {
        ClientWorld world = client.world;
        if (world == null || client.gameRenderer == null) {
            return;
        }
        BlockPos center = client.gameRenderer.getCamera().getBlockPos();
        int r = (radius >> 4) + 1;
        int cx = center.getX() >> 4;
        int cy = center.getY() >> 4;
        int cz = center.getZ() >> 4;
        int submits = 0;
        int gen = generation;
        for (int dx = -r; dx <= r && submits < MAX_SUBMITS_PER_TICK; dx++) {
            for (int dz = -r; dz <= r && submits < MAX_SUBMITS_PER_TICK; dz++) {
                if (!world.getChunkManager().isChunkLoaded(cx + dx, cz + dz)) {
                    continue;
                }
                WorldChunk chunk = world.getChunk(cx + dx, cz + dz);
                for (int dy = -r; dy <= r; dy++) {
                    int sy = cy + dy;
                    if (sy < world.getBottomSectionCoord() || sy >= world.getTopSectionCoord()) {
                        continue;
                    }
                    long key = ChunkSectionPos.asLong(cx + dx, sy, cz + dz);
                    boolean known = results.containsKey(key);
                    if ((known && !dirty.remove(key)) || pending.contains(key)) {
                        continue;
                    }
                    ChunkSection section = chunk.getSection(world.sectionCoordToIndex(sy));
                    if (section.isEmpty()) {
                        results.put(key, EMPTY);
                        continue;
                    }
                    PalettedContainer<BlockState> copy = section.getBlockStateContainer().copy();
                    int baseX = (cx + dx) << 4;
                    int baseY = sy << 4;
                    int baseZ = (cz + dz) << 4;
                    pending.add(key);
                    submits++;
                    EXECUTOR.execute(() -> {
                        try {
                            long[] found = scan(copy, baseX, baseY, baseZ);
                            if (gen == generation) {
                                results.put(key, found);
                            }
                        } finally {
                            pending.remove(key);
                        }
                    });
                }
            }
        }
        // Forget sections far away, so memory stays flat however far you travel.
        int keep = r + 3;
        results.keySet().removeIf(key -> Math.abs(ChunkSectionPos.unpackX(key) - cx) > keep
                || Math.abs(ChunkSectionPos.unpackZ(key) - cz) > keep || Math.abs(ChunkSectionPos.unpackY(key) - cy) > keep);
    }

    /** Background thread: only the copy is read here. */
    private static long[] scan(PalettedContainer<BlockState> copy, int baseX, int baseY, int baseZ) {
        long[] buffer = null;
        int n = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Integer color = OreColors.of(copy.get(x, y, z));
                    if (color != null) {
                        if (buffer == null) {
                            buffer = new long[32];
                        } else if (n + 2 > buffer.length) {
                            buffer = java.util.Arrays.copyOf(buffer, buffer.length * 2);
                        }
                        buffer[n++] = BlockPos.asLong(baseX + x, baseY + y, baseZ + z);
                        buffer[n++] = color;
                    }
                }
            }
        }
        return buffer == null ? EMPTY : java.util.Arrays.copyOf(buffer, n);
    }

    /** Render thread: every ore within {@code radius} blocks of the camera. */
    public static void forEach(BlockPos center, int radius, OreConsumer consumer) {
        long r2 = (long) radius * radius;
        for (long[] found : results.values()) {
            for (int i = 0; i < found.length; i += 2) {
                long pos = found[i];
                int x = BlockPos.unpackLongX(pos);
                int y = BlockPos.unpackLongY(pos);
                int z = BlockPos.unpackLongZ(pos);
                long dx = x - center.getX();
                long dy = y - center.getY();
                long dz = z - center.getZ();
                if (dx * dx + dy * dy + dz * dz <= r2) {
                    consumer.accept(x, y, z, (int) found[i + 1]);
                }
            }
        }
    }

    public interface OreConsumer {
        void accept(int x, int y, int z, int color);
    }

    /** For tests and the Lag Doctor: how many sections are cached. */
    public static int cachedSections() {
        return results.size();
    }
}
