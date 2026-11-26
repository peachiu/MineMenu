package com.minexray.scan;

import com.minexray.BlockFilter;
import com.minexray.EspCategory;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Groups touching highlighted blocks of the same kind into veins ("Diamond x6"), in the background, a few times
 * a second at most and only when something changed. Labels, tracers, the HUD, the minimap and the scanner list
 * all read the latest result.
 */
public final class VeinMerger {
    /** One vein: its block, size, middle, and the block nearest to you. */
    public record Vein(Block block, int color, EspCategory category, int count, double cx, double cy, double cz,
                       BlockPos nearest, double distance) {
    }

    private static final int MAX_BLOCKS = 50_000;
    private static volatile List<Vein> veins = List.of();
    private static final AtomicBoolean running = new AtomicBoolean();
    private static int lastChanges = -1;
    private static double lastX = Double.NaN;
    private static double lastY;
    private static double lastZ;
    private static int lastRadius;
    private static long lastRun;

    private VeinMerger() {
    }

    /** Nearest first. */
    public static List<Vein> veins() {
        return veins;
    }

    public static void clear() {
        veins = List.of();
        lastChanges = -1;
    }

    /**
     * Client thread: starts a new grouping if the cache changed or you moved, at most every 250 ms.
     * {@code onlyDrawn}: only the categories the ESP modes currently show (else everything scanned).
     */
    public static void request(double x, double y, double z, int radius, boolean onlyDrawn, boolean force) {
        long now = System.currentTimeMillis();
        boolean moved = Double.isNaN(lastX) || Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ) > 2;
        if (!force && (now - lastRun < 250 || (!moved && ScanCache.changes() == lastChanges && radius == lastRadius))) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        lastRun = now;
        lastChanges = ScanCache.changes();
        lastX = x;
        lastY = y;
        lastZ = z;
        lastRadius = radius;
        Mesher.Settings settings = Mesher.settings();
        SectionScanner.EXECUTOR.execute(() -> {
            try {
                veins = merge(x, y, z, radius, onlyDrawn ? settings : null);
            } catch (Throwable t) {
                com.minexray.MineXrayClient.backgroundError("vein finder", t);
            } finally {
                running.set(false);
            }
        });
    }

    /** Background thread. */
    static List<Vein> merge(double px, double py, double pz, int radius, Mesher.Settings drawn) {
        BlockFilter filter = BlockFilter.get();
        long r2 = (long) radius * radius;
        int scx = (int) Math.floor(px) >> 4, scy = (int) Math.floor(py) >> 4, scz = (int) Math.floor(pz) >> 4;
        int rs = (radius >> 4) + 1;
        Map<Long, Integer> blocks = new HashMap<>();
        Map<Long, Integer> states = new HashMap<>();
        for (SectionData data : ScanCache.all()) {
            if (data.espIdx.length == 0 || Math.abs(ChunkSectionPos.unpackX(data.key) - scx) > rs
                    || Math.abs(ChunkSectionPos.unpackY(data.key) - scy) > rs || Math.abs(ChunkSectionPos.unpackZ(data.key) - scz) > rs) {
                continue;
            }
            for (int i = 0; i < data.espIdx.length && blocks.size() < MAX_BLOCKS; i++) {
                int raw = data.espState[i];
                if (drawn != null) {
                    EspCategory category = filter.category(raw);
                    if (!(category.oreLayer() ? drawn.ore() : drawn.block())) {
                        continue;
                    }
                }
                double dx = data.x(i) + 0.5 - px, dy = data.y(i) + 0.5 - py, dz = data.z(i) + 0.5 - pz;
                if (dx * dx + dy * dy + dz * dz <= r2) {
                    long pos = BlockPos.asLong(data.x(i), data.y(i), data.z(i));
                    blocks.put(pos, filter.group(raw));
                    states.put(pos, raw);
                }
            }
        }
        List<Vein> result = new ArrayList<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        while (!blocks.isEmpty()) {
            long start = blocks.keySet().iterator().next();
            int group = blocks.remove(start);
            int raw = states.get(start);
            queue.add(start);
            int count = 0;
            double sx = 0, sy = 0, sz = 0;
            double best = Double.MAX_VALUE;
            long nearest = start;
            while (!queue.isEmpty()) {
                long pos = queue.poll();
                int x = BlockPos.unpackLongX(pos), y = BlockPos.unpackLongY(pos), z = BlockPos.unpackLongZ(pos);
                count++;
                sx += x;
                sy += y;
                sz += z;
                double d = (x + 0.5 - px) * (x + 0.5 - px) + (y + 0.5 - py) * (y + 0.5 - py) + (z + 0.5 - pz) * (z + 0.5 - pz);
                if (d < best) {
                    best = d;
                    nearest = pos;
                }
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int oz = -1; oz <= 1; oz++) {
                            long next = BlockPos.asLong(x + ox, y + oy, z + oz);
                            Integer g = blocks.get(next);
                            if (g != null && g == group) {
                                blocks.remove(next);
                                queue.add(next);
                            }
                        }
                    }
                }
            }
            Block block = group >= 0 ? Registries.BLOCK.get(group) : BlockFilter.groupOf(Block.getStateFromRawId(raw).getBlock());
            result.add(new Vein(block, filter.color(raw), filter.category(raw), count, sx / count + 0.5, sy / count + 0.5,
                    sz / count + 0.5, BlockPos.fromLong(nearest), Math.sqrt(best)));
        }
        result.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        return List.copyOf(result);
    }
}
