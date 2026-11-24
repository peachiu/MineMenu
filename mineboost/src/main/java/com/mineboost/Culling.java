package com.mineboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Smart Culling: don't draw entities and block entities (chests, signs...) that are completely hidden
 * behind solid blocks. Vanilla only skips things outside your view, not things behind walls, so in caves,
 * villages and bases this saves a lot of drawing. Only full, opaque blocks hide things: glass, leaves,
 * slabs and so on never do.
 */
public final class Culling {
    /** Each answer is reused for this long before checking again (cheap, and still feels instant). */
    private static final long RECHECK_NANOS = 100_000_000L;
    private static final long CLEANUP_NANOS = 3_000_000_000L;
    /** Always draw anything this close, so nothing next to you ever flickers. */
    private static final double ALWAYS_VISIBLE_DISTANCE_SQ = 6 * 6;

    /**
     * Answers packed in a long: bit 0 = visible, the rest = when it expires (nanoTime >> 1). Primitive maps, no
     * objects per check. Each answer expires 100-200 ms after it was made (a bit of random spread), and old ones
     * are dropped a few at a time; before 3.4 everything was cleared at once every 3 s, so every entity was
     * re-checked in the same frame (a stutter every 3 s with many mobs around).
     */
    private static final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap entities = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();
    private static final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap blockEntities = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private static long lastCleanup;
    private static int spread;

    private Culling() {
    }

    public static boolean isEntityVisible(Entity entity, double camX, double camY, double camZ) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) {
            return true;
        }
        // Never hide: you, players, bosses, glowing things (Mob Glow, Ore Vision) and anything you ride.
        if (entity == client.getCameraEntity() || entity instanceof PlayerEntity || entity instanceof EnderDragonEntity
                || entity instanceof WitherEntity || entity.isGlowing() || client.hasOutline(entity)
                || entity.hasPassengers() || entity.hasVehicle()) {
            return true;
        }
        Box box = entity.getBoundingBox();
        Vec3d camera = new Vec3d(camX, camY, camZ);
        Vec3d center = box.getCenter();
        if (center.squaredDistanceTo(camera) < ALWAYS_VISIBLE_DISTANCE_SQ) {
            return true;
        }
        long now = System.nanoTime();
        cleanup(now);
        long answer = entities.get(entity.getId());
        if (answer != 0 && (answer >>> 1) > (now >>> 1)) {
            return (answer & 1) == 1;
        }
        // Visible if the top, middle or bottom of it can be seen.
        boolean visible = canSee(world, camera, new Vec3d(center.x, box.maxY - 0.05, center.z))
                || canSee(world, camera, center)
                || canSee(world, camera, new Vec3d(center.x, box.minY + 0.05, center.z));
        entities.put(entity.getId(), pack(visible, now));
        return visible;
    }

    public static boolean isBlockEntityVisible(BlockPos pos, Vec3d camera) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return true;
        }
        Vec3d center = Vec3d.ofCenter(pos);
        if (center.squaredDistanceTo(camera) < ALWAYS_VISIBLE_DISTANCE_SQ) {
            return true;
        }
        long now = System.nanoTime();
        cleanup(now);
        long key = pos.asLong();
        long answer = blockEntities.get(key);
        if (answer != 0 && (answer >>> 1) > (now >>> 1)) {
            return (answer & 1) == 1;
        }
        boolean visible = canSee(world, camera, center) || canSee(world, camera, center.add(0, 0.45, 0));
        blockEntities.put(key, pack(visible, now));
        return visible;
    }

    private static long pack(boolean visible, long now) {
        spread = (spread + 37) % 100;
        long expires = now + RECHECK_NANOS + spread * 1_000_000L;
        return ((expires >>> 1) << 1) | (visible ? 1 : 0);
    }

    /** Drops expired answers (they'd be re-checked anyway), so the maps don't grow while you travel. */
    private static void cleanup(long now) {
        if (now - lastCleanup > CLEANUP_NANOS) {
            long limit = now >>> 1;
            entities.values().removeIf(v -> (v >>> 1) < limit);
            blockEntities.values().removeIf(v -> (v >>> 1) < limit);
            lastCleanup = now;
        }
    }

    public static void clear() {
        entities.clear();
        blockEntities.clear();
    }

    /**
     * Walks the blocks on the straight line from {@code from} to {@code to} (Amanatides-Woo voxel
     * traversal). Blocked if any block in between, other than the start and end ones, is a full opaque cube.
     */
    static boolean canSee(ClientWorld world, Vec3d from, Vec3d to) {
        int x = MathHelper.floor(from.x);
        int y = MathHelper.floor(from.y);
        int z = MathHelper.floor(from.z);
        int endX = MathHelper.floor(to.x);
        int endY = MathHelper.floor(to.y);
        int endZ = MathHelper.floor(to.z);
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        int stepX = (int) Math.signum(dx);
        int stepY = (int) Math.signum(dy);
        int stepZ = (int) Math.signum(dz);
        double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double tMaxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? (x + 1 - from.x) : (from.x - x)) * tDeltaX;
        double tMaxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? (y + 1 - from.y) : (from.y - y)) * tDeltaY;
        double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? (z + 1 - from.z) : (from.z - z)) * tDeltaZ;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int steps = 0; steps < 1024; steps++) {
            // t runs from 0 (start) to 1 (target). Past 1 means we're already in the target's block.
            if (Math.min(tMaxX, Math.min(tMaxY, tMaxZ)) > 1.0) {
                return true;
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (x == endX && y == endY && z == endZ) {
                return true; // reached the target's own block: nothing in the way
            }
            pos.set(x, y, z);
            if (world.getBlockState(pos).isOpaqueFullCube(world, pos)) {
                return false;
            }
        }
        return true;
    }
}
