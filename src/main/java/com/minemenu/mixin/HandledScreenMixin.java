package com.minemenu.mixin;

import com.minemenu.DeleteMode;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Delete Items mode: a right-click on an inventory item deletes it instead of its normal action. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow
    private Slot getSlotAt(double x, double y) {
        throw new AssertionError();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void minemenu$deleteOnRightClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        HandledScreen<?> self = (HandledScreen<?>) (Object) this;
        if (button == 1 && DeleteMode.appliesTo(self)) {
            DeleteMode.onRightClick(getSlotAt(mouseX, mouseY));
            cir.setReturnValue(true); // in delete mode, right-click never does its normal thing
        }
    }
}
