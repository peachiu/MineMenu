package com.minexray.mixin;

import com.minexray.mode.ClassicXray;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Classic X-Ray: no smooth-lighting shadows while it's on (your Smooth Lighting option itself isn't changed). */
@Mixin(MinecraftClient.class)
public class AmbientOcclusionMixin {
    @Inject(method = "isAmbientOcclusionEnabled", at = @At("HEAD"), cancellable = true)
    private static void minexray$noAo(CallbackInfoReturnable<Boolean> cir) {
        if (ClassicXray.active()) {
            cir.setReturnValue(false);
        }
    }
}
