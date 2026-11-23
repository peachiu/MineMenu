package com.mineboost.mixin;

import com.mineboost.Idle;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Idle Mode: clicking, scrolling or moving the mouse counts as "you're playing". */
@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void mineboost$button(long window, int button, int action, int mods, CallbackInfo ci) {
        Idle.poke();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"))
    private void mineboost$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Idle.poke();
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void mineboost$move(long window, double x, double y, CallbackInfo ci) {
        Idle.poke();
    }
}
