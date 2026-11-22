package com.minemenu.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Delete Items mode see which inventory slot the mouse is over, and where the screen is drawn. */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("focusedSlot")
    Slot minemenu$getFocusedSlot();

    @Accessor("x")
    int minemenu$getX();

    @Accessor("y")
    int minemenu$getY();
}
