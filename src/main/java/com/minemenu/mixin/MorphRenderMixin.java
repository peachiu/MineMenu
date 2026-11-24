package com.minemenu.mixin;

import com.minemenu.player.Morph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Morph: when your own body is about to be drawn, the mob copy is drawn in its place. */
@Mixin(LivingEntityRenderer.class)
public class MorphRenderMixin {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void minemenu$morph(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
                                int light, CallbackInfo ci) {
        if (Morph.body() != null && entity == MinecraftClient.getInstance().player) {
            Morph.render(yaw, tickDelta, matrices, consumers, light);
            ci.cancel();
        }
    }
}
