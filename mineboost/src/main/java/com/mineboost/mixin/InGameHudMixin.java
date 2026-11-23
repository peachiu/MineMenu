package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide Vignette: no dark shading around the edges of the screen (a small saving, and a clearer view). */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void mineboost$hideVignette(CallbackInfo ci) {
        if (BoostConfig.get().hideVignette) {
            ci.cancel();
        }
    }
}
