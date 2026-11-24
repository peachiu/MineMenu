package com.minemenu.mixin;

import com.minemenu.hud.HealthBars;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob Health Bars: drawn right after the mob, with the mob's own position and light. */
@Mixin(LivingEntityRenderer.class)
public class HealthBarMixin {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL"))
    private void minemenu$healthBar(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                    VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (HealthBars.on) {
            HealthBars.render(entity, tickDelta, matrices, consumers, light);
        }
    }
}
