package com.minemenu.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.minemenu.player.PlayerModules;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/** Full Bright: the lightmap reads a gamma far above the slider's 100%. */
@Mixin(LightmapTextureManager.class)
public class FullBrightMixin {
    @ModifyExpressionValue(method = "update",
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getGamma()Lnet/minecraft/client/option/SimpleOption;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 0))
    private Object minemenu$fullBright(Object gamma) {
        return PlayerModules.fullBrightOn || PlayerModules.forceFullBright ? (Object) 16.0 : gamma;
    }
}
