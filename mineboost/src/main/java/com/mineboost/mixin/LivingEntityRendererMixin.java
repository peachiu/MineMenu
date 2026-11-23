package com.mineboost.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mineboost.BoostConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.EnergySwirlOverlayFeatureRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.SheepWoolFeatureRenderer;
import net.minecraft.client.render.entity.feature.SlimeOverlayFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("RETURN"), cancellable = true)
    private void mineboost$hideNameTag(CallbackInfoReturnable<Boolean> cir) {
        if (BoostConfig.get().hideNameTags) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Simple Far Mobs: each "feature" (armor, held items, heads, saddles, elytras, stuck arrows, clothing...)
     * draws the mob's model again on top of itself. Far away you can't see them, so skip them there.
     * A few layers are kept because without them the mob looks wrong or you'd miss a danger:
     * sheep wool, glowing eyes, the slime's outer body and the charged creeper's swirl. Players always keep theirs.
     */
    @WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/Entity;FFFFFF)V"))
    private void mineboost$skipFarFeatures(FeatureRenderer<?, ?> feature, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                           int light, Entity entity, float limbAngle, float limbDistance, float tickDelta,
                                           float animationProgress, float headYaw, float headPitch, Operation<Void> original) {
        int max = BoostConfig.get().farMobDetail;
        if (max > 0 && !(entity instanceof PlayerEntity)
                && !(feature instanceof SheepWoolFeatureRenderer || feature instanceof EyesFeatureRenderer
                || feature instanceof SlimeOverlayFeatureRenderer || feature instanceof EnergySwirlOverlayFeatureRenderer)) {
            Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
            if (entity.squaredDistanceTo(camera) > (double) max * max) {
                return;
            }
        }
        original.call(feature, matrices, vertexConsumers, light, entity, limbAngle, limbDistance, tickDelta,
                animationProgress, headYaw, headPitch);
    }
}
