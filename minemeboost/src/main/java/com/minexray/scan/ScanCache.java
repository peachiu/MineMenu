package com.minexray.scan;

import net.minecraft.util.math.ChunkSectionPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scan results per section. A section is scanned once and again only when one of its blocks changes, the block
 * list changes, or its chunk reloads. Cleared on leaving the world or changing dimension; kept under a memory
 * limit by dropping the farthest sections first.
 */
public final class ScanCache {
    private static final Map<Long, SectionData> sections = new ConcurrentHashMap<>();
    private static final AtomicLong bytes = new AtomicLong();
    /** Bumped whenever everything is thrown away: results of scans started before are discarded. */
    private static volatile int generation;
    /** Bumped on every change, so the renderer and vein finder know to look again. */
    private static volatile int changes;

    private ScanCache() {
    }

    public static SectionData get(long key) {
        return sections.get(key);
    }

    public static boolean has(long key) {
        return sections.containsKey(key);
    }

    public static Collection<SectionData> all() {
        return sections.values();
    }

    public static int size() {
        return sections.size();
    }

    public static long bytes() {
        return bytes.get();
    }

    public static int generation() {
        return generation;
    }

    public static int changes() {
        return changes;
    }

    static void put(SectionData data, int startedGeneration) {
        if (startedGeneration != generation) {
            return;
        }
        SectionData old = sections.put(data.key, data);
        bytes.addAndGet(data.bytes() - (old == null ? 0 : old.bytes()));
        changes++;
    }

    /** The mesh of a section changed size: keep the memory count right. */
    static void meshReplaced(SectionData data, MeshData old, MeshData now) {
        if (sections.get(data.key) == data) {
            bytes.addAndGet((now == null ? 0 : now.bytes()) - (old == null ? 0 : old.bytes()));
        }
        changes++;
    }

    public static void remove(long key) {
        SectionData old = sections.remove(key);
        if (old != null) {
            bytes.addAndGet(-old.bytes());
            changes++;
        }
    }

    public static void forgetChunk(int chunkX, int chunkZ) {
        List<Long> keys = new ArrayList<>();
        for (Long key : sections.keySet()) {
            if (ChunkSectionPos.unpackX(key) == chunkX && ChunkSectionPos.unpackZ(key) == chunkZ) {
                keys.add(key);
            }
        }
        keys.forEach(ScanCache::remove);
    }

    public static void clear() {
        generation++;
        sections.clear();
        bytes.set(0);
        changes++;
    }

    /**
     * Drops sections more than {@code keep} sections away from (cx, cy, cz), then the farthest ones while the
     * cache is over {@code limitBytes}.
     */
    static void trim(int cx, int cy, int cz, int keep, long limitBytes) {
        List<Long> far = new ArrayList<>();
        for (Long key : sections.keySet()) {
            if (Math.abs(ChunkSectionPos.unpackX(key) - cx) > keep || Math.abs(ChunkSectionPos.unpackZ(key) - cz) > keep
                    || Math.abs(ChunkSectionPos.unpackY(key) - cy) > keep) {
                far.add(key);
            }
        }
        far.forEach(ScanCache::remove);
        if (bytes.get() <= limitBytes) {
            return;
        }
        List<SectionData> byDistance = new ArrayList<>(sections.values());
        byDistance.sort((a, b) -> Long.compare(distance2(b.key, cx, cy, cz), distance2(a.key, cx, cy, cz)));
        for (SectionData data : byDistance) {
            if (bytes.get() <= limitBytes * 9 / 10) {
                break;
            }
            remove(data.key);
        }
    }

    static long distance2(long key, int cx, int cy, int cz) {
        long dx = ChunkSectionPos.unpackX(key) - cx;
        long dy = ChunkSectionPos.unpackY(key) - cy;
        long dz = ChunkSectionPos.unpackZ(key) - cz;
        return dx * dx + dy * dy + dz * dz;
    }
}
