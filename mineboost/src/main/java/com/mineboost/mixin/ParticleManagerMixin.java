package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;

import java.util.Map;
import java.util.Queue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
        if (config.particleLimit > 0) {
            int count = newParticles.size();
            for (Queue<Particle> queue : particles.values()) {
                count += queue.size();
            }
            if (count >= config.particleLimit) {
                ci.cancel();
            }
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
