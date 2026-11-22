package com.minemenu.mixin;

import com.minemenu.FlyMining;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla mines 5x slower whenever you're not standing on the ground. With Fly on, that makes
 * mining in the air painfully slow, so undo exactly that penalty (client and server both use this).
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void minemenu$noAirMiningPenalty(BlockState block, CallbackInfoReturnable<Float> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!self.isOnGround() && FlyMining.applies(self)) {
            cir.setReturnValue(cir.getReturnValueF() * 5.0F);
        }
    }
}
