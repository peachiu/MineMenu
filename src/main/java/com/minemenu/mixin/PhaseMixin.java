package com.minemenu.mixin;

import com.minemenu.LocalHost;
import com.minemenu.player.PlayerModules;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TransparentBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase: doors, trapdoors, fence gates, glass, panes and iron bars have no collision for you (on the client and
 * the server, so both agree on where you are). Everyone else still bumps into them. Costs one boolean check
 * per collision test while Phase is off.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class PhaseMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void minemenu$phase(BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!PlayerModules.phaseOn || !(context instanceof EntityShapeContext entityContext) || entityContext.getEntity() == null) {
            return;
        }
        BlockState state = (BlockState) (Object) this;
        var block = state.getBlock();
        if ((block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
                || block instanceof PaneBlock || block instanceof TransparentBlock) && LocalHost.is(entityContext.getEntity())) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }
}
