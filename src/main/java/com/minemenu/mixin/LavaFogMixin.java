package com.minemenu.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.minemenu.player.PlayerModules;
import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lava Swim: in lava you see as far as a spectator does (the first isSpectator() check in applyFog is the lava one). */
@Mixin(BackgroundRenderer.class)
public class LavaFogMixin {
    @ModifyExpressionValue(method = "applyFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSpectator()Z", ordinal = 0))
    private static boolean minemenu$clearLavaView(boolean spectator) {
        return spectator || PlayerModules.lavaSwimOn;
    }
}
