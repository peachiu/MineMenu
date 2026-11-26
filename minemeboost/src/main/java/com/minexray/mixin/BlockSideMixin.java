package com.minexray.mixin;

import com.minexray.BlockFilter;
import com.minexray.mode.ClassicXray;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Classic X-Ray: a shown block draws every side that faces a hidden block (else ores would look hollow). */
@Mixin(Block.class)
public class BlockSideMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void minexray$sideNextToHidden(BlockState state, BlockView world, BlockPos pos, Direction side, BlockPos otherPos,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (ClassicXray.active() && BlockFilter.get().isVisible(state) && !BlockFilter.get().isVisible(world.getBlockState(otherPos))) {
            cir.setReturnValue(true);
        }
    }
}
