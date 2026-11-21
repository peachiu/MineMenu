package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Hide Rain & Snow": skip drawing weather and the splash particles. It still rains (crops, mobs...). */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void mineboost$hideWeather(LightmapTextureManager manager, float tickDelta, double cameraX, double cameraY,
                                       double cameraZ, CallbackInfo ci) {
        if (BoostConfig.get().hideWeather) {
            ci.cancel();
        }
    }

    @Inject(method = "tickRainSplashing", at = @At("HEAD"), cancellable = true)
    private void mineboost$hideSplashes(Camera camera, CallbackInfo ci) {
        if (BoostConfig.get().hideWeather) {
            ci.cancel();
        }
    }
}
