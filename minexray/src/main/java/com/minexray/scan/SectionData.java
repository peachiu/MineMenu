package com.minexray.scan;

import net.minecraft.util.math.ChunkSectionPos;

/**
 * What the scanner found in one 16x16x16 section: the highlighted blocks (local index + state id), and bitsets for
 * lava, water and cave air. Immutable once made; its geometry ({@link #mesh}) is added later by the mesher.
 */
public final class SectionData {
    static final short[] NO_BLOCKS = new short[0];
    static final int[] NO_STATES = new int[0];

    public final long key;
    public final int baseX;
    public final int baseY;
    public final int baseZ;
    /** Local index (y << 8 | z << 4 | x) and block state id of every highlighted block. */
    public final short[] espIdx;
    public final int[] espState;
    /** 4096-bit sets (64 longs), or null when there's none. */
    public final long[] lava;
    public final long[] water;
    public final long[] cave;
    public final int caveCount;
    /** Filter version this was scanned with. */
    final int scanSignature;
    /** Geometry, replaced as a whole when rebuilt. */
    volatile MeshData mesh;

    SectionData(long key, short[] espIdx, int[] espState, long[] lava, long[] water, long[] cave, int caveCount, int scanSignature) {
        this.key = key;
        this.baseX = ChunkSectionPos.unpackX(key) << 4;
        this.baseY = ChunkSectionPos.unpackY(key) << 4;
        this.baseZ = ChunkSectionPos.unpackZ(key) << 4;
        this.espIdx = espIdx;
        this.espState = espState;
        this.lava = lava;
        this.water = water;
        this.cave = cave;
        this.caveCount = caveCount;
        this.scanSignature = scanSignature;
    }

    static SectionData empty(long key, int signature) {
        return new SectionData(key, NO_BLOCKS, NO_STATES, null, null, null, 0, signature);
    }

    public boolean isEmpty() {
        return espIdx.length == 0 && lava == null && water == null && cave == null;
    }

    public MeshData mesh() {
        return mesh;
    }

    public int x(int i) {
        return baseX + (espIdx[i] & 15);
    }

    public int y(int i) {
        return baseY + ((espIdx[i] >> 8) & 15);
    }

    public int z(int i) {
        return baseZ + ((espIdx[i] >> 4) & 15);
    }

    static boolean bit(long[] set, int index) {
        return set != null && (set[index >> 6] & (1L << (index & 63))) != 0;
    }

    static void set(long[] set, int index) {
        set[index >> 6] |= 1L << (index & 63);
    }

    /** Rough memory use, for the cache limit. */
    long bytes() {
        long bytes = 96 + espIdx.length * 2L + espState.length * 4L;
        bytes += (lava != null ? 528 : 0) + (water != null ? 528 : 0) + (cave != null ? 528 : 0);
        MeshData m = mesh;
        return bytes + (m == null ? 0 : m.bytes());
    }
}
