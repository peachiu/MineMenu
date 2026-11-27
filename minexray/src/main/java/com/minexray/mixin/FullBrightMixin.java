package com.minexray.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.minexray.mode.ClassicXray;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/** Classic X-Ray: full brightness while it's on (your Brightness option itself isn't changed). */
@Mixin(LightmapTextureManager.class)
public class FullBrightMixin {
    @ModifyExpressionValue(method = "update",
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getGamma()Lnet/minecraft/client/option/SimpleOption;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 0))
    private Object minexray$fullBright(Object gamma) {
        return ClassicXray.fullBright() ? (Object) 16.0 : gamma;
    }
}
