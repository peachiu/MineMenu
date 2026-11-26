package com.minexray.scan;

/**
 * One section's geometry, built off the main thread: vertices as (x, y, z, ARGB) ints (floats stored as raw
 * bits), section-local. Lines are pairs of vertices, fill faces are quads. The render thread copies these into
 * one GPU buffer per region (see EspRenderer), so the world is drawn with a few draw calls.
 */
public final class MeshData {
    public static final int[] NONE = new int[0];
    public static final int ESP_LINES = 0;
    public static final int ESP_FILL = 1;
    public static final int FLUID_LINES = 2;
    public static final int FLUID_FILL = 3;
    public static final int CAVE_LINES = 4;
    public static final int LAYERS = 5;

    /** Per layer, 4 ints per vertex. */
    public final int[][] layers;
    /** Settings version the mesh was built with. */
    public final int version;
    /** Highlighted blocks drawn in this section (for the block limit). */
    public final int espBlocks;

    MeshData(int[][] layers, int version, int espBlocks) {
        this.layers = layers;
        this.version = version;
        this.espBlocks = espBlocks;
    }

    public boolean has(int layer) {
        return layers[layer].length > 0;
    }

    long bytes() {
        long bytes = 64;
        for (int[] layer : layers) {
            bytes += 16 + layer.length * 4L;
        }
        return bytes;
    }
}
