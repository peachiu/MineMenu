package com.minemenu.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.minemenu.ClientCheats;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Zoom: a narrower field of view while the zoom key is held. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private double minemenu$zoom(double fov, Camera camera, float tickDelta, boolean changingFov) {
        return changingFov && ClientCheats.isZooming() ? fov / ClientCheats.zoomFactor() : fov;
    }
}
