package com.minemenu.mixin;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.LocalHost;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Water Walk: the top of water and lava is solid for you (unless you sneak). */
@Mixin(FluidBlock.class)
public abstract class FluidBlockMixin {
    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true)
    private void minemenu$waterWalk(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
                                    CallbackInfoReturnable<VoxelShape> cir) {
        if (!cir.getReturnValue().isEmpty() || !CheatState.isOn(Feature.WATER_WALK)
                || !(context instanceof EntityShapeContext entityContext)) {
            return;
        }
        Entity entity = entityContext.getEntity();
        // Only the surface block, only while you're on top of it, and never while sneaking.
        if (entity != null && !entity.isSneaking() && LocalHost.is(entity)
                && world.getFluidState(pos.up()).isEmpty()
                && context.isAbove(VoxelShapes.fullCube(), pos, true)) {
            cir.setReturnValue(VoxelShapes.fullCube());
        }
    }
}
