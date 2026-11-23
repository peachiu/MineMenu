package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Disable All Particles": stronger than vanilla's Minimal, which still shows many particles. */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    @Shadow
    @Final
    private Map<ParticleTextureSheet, Queue<Particle>> particles;

    @Shadow
    @Final
    private Queue<Particle> newParticles;

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void mineboost$blockParticle(Particle particle, CallbackInfo ci) {
        BoostConfig config = BoostConfig.get();
        if (config.disableParticles) {
            ci.cancel();
            return;
        }
        // Particle Range: particles far away are barely visible; don't create them at all.
        if (config.particleRange > 0) {
            var camera = net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
            ParticleAccessor p = (ParticleAccessor) particle;
            double dx = p.mineboost$getX() - camera.x, dy = p.mineboost$getY() - camera.y, dz = p.mineboost$getZ() - camera.z;
            if (dx * dx + dy * dy + dz * dz > (double) config.particleRange * config.particleRange) {
                ci.cancel();
                return;
            }
        }
        // Particle Limit: a TNT chain or a big farm can spawn thousands at once.
        // At the limit, particles close to you still get in (up to 25% over); the end-of-tick trim
        // below then removes the farthest ones. So the ones you can actually see win.
        if (config.particleLimit > 0) {
            int count = mineboost$count();
            if (count >= config.particleLimit) {
                if (count >= config.particleLimit + config.particleLimit / 4 || mineboost$distanceSq(particle) > NEAR_SQ) {
                    ci.cancel();
                }
            }
        }
    }

    @Unique
    private static final double NEAR_SQ = 16 * 16;

    @Unique
    private int mineboost$count() {
        int count = newParticles.size();
        for (Queue<Particle> queue : particles.values()) {
            count += queue.size();
        }
        return count;
    }

    @Unique
    private static double mineboost$distanceSq(Particle particle) {
        var camera = net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        ParticleAccessor p = (ParticleAccessor) particle;
        double dx = p.mineboost$getX() - camera.x, dy = p.mineboost$getY() - camera.y, dz = p.mineboost$getZ() - camera.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Over the Particle Limit? Remove the farthest particles first. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void mineboost$trimFarthest(CallbackInfo ci) {
        int limit = BoostConfig.get().particleLimit;
        if (limit <= 0) {
            return;
        }
        int over = mineboost$count() - limit;
        if (over <= 0) {
            return;
        }
        List<Particle> all = new ArrayList<>(limit + over);
        for (Queue<Particle> queue : particles.values()) {
            for (Particle particle : queue) {
                if (particle.isAlive()) {
                    all.add(particle);
                }
            }
        }
        double[] distance = new double[all.size()];
        Integer[] order = new Integer[all.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
            distance[i] = mineboost$distanceSq(all.get(i));
        }
        Arrays.sort(order, (a, b) -> Double.compare(distance[b], distance[a]));
        for (int i = 0; i < over && i < order.length; i++) {
            all.get(order[i]).markDead();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void mineboost$blockEffect(ParticleEffect parameters, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ,
                                       CallbackInfoReturnable<Particle> cir) {
        if (BoostConfig.get().disableParticles) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;)V",
            at = @At("HEAD"), cancellable = true)
    private void mineboost$blockEmitter(Entity entity, ParticleEffect parameters, CallbackInfo ci) {
        if (BoostConfig.get().disableParticles) {
            ci.cancel();
        }
    }

    @Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;I)V",
            at = @At("HEAD"), cancellable = true)
    private void mineboost$blockTimedEmitter(Entity entity, ParticleEffect parameters, int maxAge, CallbackInfo ci) {
        if (BoostConfig.get().disableParticles) {
            ci.cancel();
        }
    }
}
