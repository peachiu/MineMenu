package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Animated Textures" off: water, lava, fire, portals and so on stop animating. Saves texture uploads. */
@Mixin(SpriteAtlasTexture.class)
public class SpriteAtlasTextureMixin {
    @Inject(method = "tickAnimatedSprites", at = @At("HEAD"), cancellable = true)
    private void mineboost$freezeAnimations(CallbackInfo ci) {
        if (!BoostConfig.get().animatedTextures) {
            ci.cancel();
        }
    }
}
