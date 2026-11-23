package com.mineboost.mixin;

import com.mineboost.Idle;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Idle Mode: any key press counts as "you're playing". */
@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void mineboost$key(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        Idle.poke();
    }
}
