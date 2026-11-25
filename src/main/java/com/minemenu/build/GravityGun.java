package com.minemenu.build;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Gravity Gun: sneak + right-click with an empty hand grabs the mob or block you look at (up to 24 blocks); it
 * floats 4 blocks in front of you while you look around; sneak + right-click again throws it.
 * Blocks become falling blocks while held, and land as blocks again.
 */
public final class GravityGun {
    private static UUID held;

    private GravityGun() {
    }

    public static boolean holding() {
        return held != null;
    }

    /** The client saw sneak + right-click with an empty hand. */
    public static void click(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (held != null) {
            Entity entity = world.getEntity(held);
            held = null;
            if (entity != null) {
                entity.setNoGravity(false);
                entity.setVelocity(player.getRotationVector().multiply(2.5));
                entity.velocityModified = true;
            }
            return;
        }
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        double range = 24;
        HitResult blockHit = player.raycast(range, 1.0f, false);
        double blockDistance = blockHit.getType() == HitResult.Type.MISS ? range : blockHit.getPos().distanceTo(eye);
        EntityHitResult entityHit = ProjectileUtil.raycast(player, eye, eye.add(look.multiply(blockDistance)),
                new Box(eye, eye.add(look.multiply(blockDistance))).expand(1), e -> e.isAlive() && e != player && !(e instanceof net.minecraft.entity.player.PlayerEntity), blockDistance * blockDistance);
        if (entityHit != null) {
            held = entityHit.getEntity().getUuid();
            entityHit.getEntity().setNoGravity(true);
            return;
        }
        if (blockHit instanceof BlockHitResult hit && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockState state = world.getBlockState(hit.getBlockPos());
            if (state.getHardness(world, hit.getBlockPos()) < 0 || state.hasBlockEntity()) {
                return; // bedrock, chests...: no
            }
            FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, hit.getBlockPos(), state);
            falling.setNoGravity(true);
            falling.dropItem = true;
            held = falling.getUuid();
        }
    }

    /** Server tick: keep the held thing in front of you. */
    public static void tick(ServerPlayerEntity player) {
        if (held == null) {
            return;
        }
        Entity entity = player.getServerWorld().getEntity(held);
        if (entity == null || !entity.isAlive()) {
            held = null;
            return;
        }
        Vec3d target = player.getEyePos().add(player.getRotationVector().multiply(4)).subtract(0, entity.getHeight() / 2, 0);
        entity.setVelocity(target.subtract(entity.getPos()).multiply(0.5));
        entity.velocityModified = true;
        entity.fallDistance = 0;
        if (entity instanceof FallingBlockEntity falling) {
            falling.timeFalling = 1; // never times out while held
        }
        if (entity instanceof LivingEntity living) {
            living.setOnGround(false);
        }
    }

    public static void reset() {
        held = null;
    }
}
