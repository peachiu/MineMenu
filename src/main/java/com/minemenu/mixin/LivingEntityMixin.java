package com.minemenu.mixin;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.LocalHost;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spider Climb: walking into a wall counts as climbing a ladder. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "isClimbing", at = @At("RETURN"), cancellable = true)
    private void minemenu$spiderClimb(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!cir.getReturnValueZ() && self instanceof PlayerEntity && self.horizontalCollision
                && (CheatState.isOn(Feature.SPIDER_CLIMB) || com.minemenu.player.Morph.climbs()) && LocalHost.is(self)) {
            cir.setReturnValue(true);
        }
    }
}
