package com.mineboost.mixin;

import com.mineboost.BoostConfig;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Simple Dropped Items: draw a dropped stack as one item instead of a pile of up to five. */
@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Inject(method = "getRenderedAmount", at = @At("HEAD"), cancellable = true)
    private static void mineboost$oneModel(int stackSize, CallbackInfoReturnable<Integer> cir) {
        if (BoostConfig.get().simpleItems) {
            cir.setReturnValue(1);
        }
    }
}
