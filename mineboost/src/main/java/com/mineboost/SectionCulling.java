package com.mineboost;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.List;

/**
 * Section Culling: every frame the game already works out which 16x16x16 chunk sections can be seen (through
 * caves and openings, not just the view cone), but it still draws entities in the hidden ones. This skips
 * those. The set of visible sections is built once per frame, right before entities are drawn.
 * Off with Sodium (it replaces this renderer and culls entities itself) and with EntityCulling.
 */
public final class SectionCulling {
    private static final LongOpenHashSet visible = new LongOpenHashSet(4096);
    private static boolean ready;

    private SectionCulling() {
    }

    public static boolean enabled() {
        return BoostConfig.get().sectionCulling && !Compat.SODIUM && !Compat.ENTITYCULLING;
    }

    /** Called once per frame with the sections the game is about to draw. */
    public static void rebuild(List<ChunkBuilder.BuiltChunk> builtChunks) {
        visible.clear();
        for (ChunkBuilder.BuiltChunk chunk : builtChunks) {
            BlockPos origin = chunk.getOrigin();
            visible.add(ChunkSectionPos.asLong(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4));
        }
        ready = !visible.isEmpty();
    }

    public static void clear() {
        visible.clear();
        ready = false;
    }

    /** True if any of the sections the entity's box touches (corners) is visible. */
    public static boolean isVisible(Entity entity) {
        if (!ready) {
            return true;
        }
        Box box = entity.getBoundingBox();
        int minX = (int) Math.floor(box.minX) >> 4;
        int minY = (int) Math.floor(box.minY) >> 4;
        int minZ = (int) Math.floor(box.minZ) >> 4;
        int maxX = (int) Math.floor(box.maxX) >> 4;
        int maxY = (int) Math.floor(box.maxY) >> 4;
        int maxZ = (int) Math.floor(box.maxZ) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (visible.contains(ChunkSectionPos.asLong(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
