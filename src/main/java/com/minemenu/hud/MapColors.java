package com.minemenu.hud;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

/**
 * The minimap's picture, cached per chunk (16x16 colours) so the map isn't redrawn from the world every frame.
 * A chunk is recomputed only when it loads or a block in it changes; at most a few chunks per tick.
 * Colours are in NativeImage's ABGR format. Client thread only.
 */
public final class MapColors {
    private static final Long2ObjectOpenHashMap<int[]> surface = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<int[]> cave = new Long2ObjectOpenHashMap<>();
    private static int caveY = Integer.MIN_VALUE;

    private MapColors() {
    }

    public static void clear() {
        surface.clear();
        cave.clear();
    }

    public static void markDirty(BlockPos pos) {
        long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        surface.remove(key);
        cave.remove(key);
    }

    public static void forget(int chunkX, int chunkZ) {
        long key = ChunkPos.toLong(chunkX, chunkZ);
        surface.remove(key);
        cave.remove(key);
    }

    /** Cave mode follows your height: when you move up or down by 4+ blocks, cave colours are redone. */
    public static void setCaveY(int y) {
        if (Math.abs(y - caveY) >= 4) {
            caveY = y;
            cave.clear();
        }
    }

    /**
     * Colours of one chunk, or null if not cached yet. {@code budget[0]} is how many chunks may still be
     * computed this tick; computing one uses one.
     */
    public static int[] get(ClientWorld world, int chunkX, int chunkZ, boolean caveMode, int[] budget) {
        Long2ObjectOpenHashMap<int[]> cache = caveMode ? cave : surface;
        long key = ChunkPos.toLong(chunkX, chunkZ);
        int[] colors = cache.get(key);
        if (colors != null || budget[0] <= 0 || !world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
            return colors;
        }
        budget[0]--;
        colors = compute(world, world.getChunk(chunkX, chunkZ), caveMode);
        cache.put(key, colors);
        return colors;
    }

    private static int[] compute(ClientWorld world, WorldChunk chunk, boolean caveMode) {
        int[] colors = new int[256];
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int baseX = chunk.getPos().getStartX();
        int baseZ = chunk.getPos().getStartZ();
        int[] lastHeight = new int[16];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = caveMode ? caveTop(chunk, pos, baseX + x, baseZ + z) : chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
                pos.set(baseX + x, y, baseZ + z);
                BlockState state = chunk.getBlockState(pos);
                MapColor color = state.getMapColor(world, pos);
                // Shade by slope (like vanilla maps): higher than the block to the north = lighter.
                MapColor.Brightness brightness = z == 0 ? MapColor.Brightness.NORMAL
                        : y > lastHeight[x] ? MapColor.Brightness.HIGH : y < lastHeight[x] ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                lastHeight[x] = y;
                colors[z * 16 + x] = color == MapColor.CLEAR ? 0xFF000000 : color.getRenderColor(brightness);
            }
        }
        return colors;
    }

    /** Cave mode: the first floor below your height (air above, solid block). */
    private static int caveTop(WorldChunk chunk, BlockPos.Mutable pos, int x, int z) {
        for (int y = caveY + 1; y > caveY - 24; y--) {
            pos.set(x, y, z);
            if (!chunk.getBlockState(pos).isAir()) {
                pos.set(x, y + 1, z);
                if (chunk.getBlockState(pos).isAir()) {
                    return y;
                }
            }
        }
        return caveY - 24;
    }
}
