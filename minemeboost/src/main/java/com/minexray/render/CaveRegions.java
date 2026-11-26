package com.minexray.render;

import com.minexray.scan.ScanCache;
import com.minexray.scan.SectionData;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Show only large caves": sections with cave air that touch each other form one cave; a cave counts as large
 * when it holds at least the minimum volume of cave air. Worked out again only when the scan results change.
 */
final class CaveRegions {
    /** Sections with fewer cave-air blocks don't connect caves (a few stray blocks aren't a passage). */
    private static final int MIN_PER_SECTION = 8;
    private static int lastChanges = -1;
    private static int lastMinimum = -1;
    private static Set<Long> large = Set.of();

    private CaveRegions() {
    }

    static Set<Long> large(int minimumVolume) {
        if (ScanCache.changes() == lastChanges && minimumVolume == lastMinimum) {
            return large;
        }
        lastChanges = ScanCache.changes();
        lastMinimum = minimumVolume;
        Map<Long, Integer> counts = new HashMap<>();
        for (SectionData data : ScanCache.all()) {
            if (data.caveCount >= MIN_PER_SECTION) {
                counts.put(data.key, data.caveCount);
            }
        }
        Set<Long> result = new HashSet<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (Long start : counts.keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            List<Long> component = new java.util.ArrayList<>();
            int volume = 0;
            queue.add(start);
            while (!queue.isEmpty()) {
                long key = queue.poll();
                component.add(key);
                volume += counts.get(key);
                int x = ChunkSectionPos.unpackX(key), y = ChunkSectionPos.unpackY(key), z = ChunkSectionPos.unpackZ(key);
                long[] next = {ChunkSectionPos.asLong(x + 1, y, z), ChunkSectionPos.asLong(x - 1, y, z), ChunkSectionPos.asLong(x, y + 1, z),
                        ChunkSectionPos.asLong(x, y - 1, z), ChunkSectionPos.asLong(x, y, z + 1), ChunkSectionPos.asLong(x, y, z - 1)};
                for (long n : next) {
                    if (counts.containsKey(n) && seen.add(n)) {
                        queue.add(n);
                    }
                }
            }
            if (volume >= minimumVolume) {
                result.addAll(component);
            }
        }
        large = result;
        return large;
    }
}
