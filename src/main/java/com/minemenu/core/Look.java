package com.minemenu.core;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** "What is the player looking at?" for server-side hacks. */
public final class Look {
    private Look() {
    }

    /** The living entity (not a player) under the crosshair within {@code range}; blocks stop the look. */
    public static @Nullable LivingEntity livingEntity(ServerPlayerEntity player, double range) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        HitResult blockHit = player.raycast(range, 1.0f, false);
        double distance = blockHit.getType() == HitResult.Type.MISS ? range : blockHit.getPos().distanceTo(eye);
        Vec3d end = eye.add(look.multiply(distance));
        EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, new Box(eye, end).expand(1.0),
                e -> e instanceof LivingEntity && !(e instanceof PlayerEntity) && e.isAlive(), distance * distance);
        return hit != null ? (LivingEntity) hit.getEntity() : null;
    }
}
