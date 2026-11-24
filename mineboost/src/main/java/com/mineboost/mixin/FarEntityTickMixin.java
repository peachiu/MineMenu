package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Experimental "Far Entity Ticks": mobs more than 48 blocks away and behind you are updated on your side
 * (animation, movement smoothing) only every 4th tick. The server still runs them normally; when they come
 * into view they catch up. Projectiles, items, players and anything ridden always tick.
 */
@Mixin(ClientWorld.class)
public class FarEntityTickMixin {
    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private void mineboost$skipFarTicks(Entity entity, CallbackInfo ci) {
        if (!BoostConfig.get().farEntityTicks || !(entity instanceof LivingEntity) || entity instanceof PlayerEntity
                || entity.hasVehicle() || entity.hasPassengers() || (entity.age + entity.getId()) % 4 == 0) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Vec3d to = entity.getPos().subtract(camera);
        if (to.lengthSquared() < 48 * 48) {
            return;
        }
        Vec3d look = Vec3d.fromPolar(client.gameRenderer.getCamera().getPitch(), client.gameRenderer.getCamera().getYaw());
        if (to.normalize().dotProduct(look) < -0.2) { // well behind the camera
            entity.age++; // keep its age moving so its own timers don't freeze
            ci.cancel();
        }
    }
}
