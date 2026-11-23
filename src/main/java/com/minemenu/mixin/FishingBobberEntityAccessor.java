package com.minemenu.mixin;

import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Auto-Fish: "a fish is on the hook" (sent by the server to your client when the bobber dips). */
@Mixin(FishingBobberEntity.class)
public interface FishingBobberEntityAccessor {
    @Accessor("caughtFish")
    boolean minemenu$hasCaughtFish();
}
