package com.minemenu.mixin;

import com.minemenu.build.BlockRecorder;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Undo for vanilla structures: while {@link BlockRecorder} is recording (only during one structure placement),
 * remember each block before it changes. Otherwise it costs one field read per block change.
 */
@Mixin(World.class)
public abstract class WorldBlockRecorderMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("HEAD"))
    private void minemenu$recordBefore(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (BlockRecorder.recording != null) {
            BlockRecorder.beforeSet((World) (Object) this, pos);
        }
    }
}
