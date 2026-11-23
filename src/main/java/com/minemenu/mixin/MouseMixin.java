package com.minemenu.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.minemenu.ClientCheats;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom: the scroll wheel changes how close you zoom, and the mouse turns slower, like with a spyglass. */
@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void minemenu$scrollZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (vertical != 0 && ClientCheats.isZooming()) {
            ClientCheats.scrollZoom(vertical);
            ci.cancel(); // don't also change the hotbar slot
        }
    }

    @ModifyExpressionValue(method = "updateMouse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingSpyglass()Z"))
    private boolean minemenu$slowWhileZooming(boolean usingSpyglass) {
        return usingSpyglass || ClientCheats.isZooming();
    }
}
