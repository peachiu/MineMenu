package com.minexray.mixin;

import com.minexray.mode.ClassicXray;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Classic X-Ray with opacity above 0: hidden blocks go in the translucent layer, so they can be see-through. */
@Mixin(RenderLayers.class)
public class RenderLayersMixin {
    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private static void minexray$seeThrough(BlockState state, CallbackInfoReturnable<RenderLayer> cir) {
        if (ClassicXray.seeThrough(state)) {
            cir.setReturnValue(RenderLayer.getTranslucent());
        }
    }
}
