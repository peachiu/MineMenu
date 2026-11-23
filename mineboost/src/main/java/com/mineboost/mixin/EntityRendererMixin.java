package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide Name Tags: names above players and named mobs aren't drawn. */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(method = "hasLabel", at = @At("RETURN"), cancellable = true)
    private void mineboost$hideNameTag(CallbackInfoReturnable<Boolean> cir) {
        if (BoostConfig.get().hideNameTags) {
            cir.setReturnValue(false);
        }
    }
}
