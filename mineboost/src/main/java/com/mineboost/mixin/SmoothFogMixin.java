package com.mineboost.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mineboost.BoostConfig;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Smooth Fog Edge: on land the fog starts at 60% of the render distance instead of about 90%, so chunks that
 * load (or appear when Smart Render Distance lowers the distance) fade in instead of popping. Costs nothing.
 */
@Mixin(BackgroundRenderer.class)
public class SmoothFogMixin {
    @ModifyArg(method = "applyFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogStart(F)V"))
    private static float mineboost$smoothFog(float start, @Local(argsOnly = true) Camera camera,
                                             @Local(argsOnly = true) BackgroundRenderer.FogType fogType,
                                             @Local(argsOnly = true, ordinal = 0) float viewDistance, @Local(argsOnly = true) boolean thickFog) {
        if (BoostConfig.get().smoothFog && fogType == BackgroundRenderer.FogType.FOG_TERRAIN && !thickFog
                && camera.getSubmersionType() == CameraSubmersionType.NONE) {
            return Math.min(start, viewDistance * 0.6f);
        }
        return start;
    }
}
