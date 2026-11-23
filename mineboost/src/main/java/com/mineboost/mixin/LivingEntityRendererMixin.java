package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hide Name Tags for mobs and players: their renderer decides on labels itself, without asking
 * {@link EntityRendererMixin}'s method, so it needs its own hook.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("RETURN"), cancellable = true)
    private void mineboost$hideNameTag(CallbackInfoReturnable<Boolean> cir) {
        if (BoostConfig.get().hideNameTags) {
            cir.setReturnValue(false);
        }
    }
}
