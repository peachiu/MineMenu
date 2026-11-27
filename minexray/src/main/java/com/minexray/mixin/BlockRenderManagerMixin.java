package com.minexray.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.minexray.mode.ClassicXray;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Classic X-Ray: hidden blocks are skipped (or drawn see-through) while chunks are built. */
@Mixin(BlockRenderManager.class)
public class BlockRenderManagerMixin {
    @WrapMethod(method = "renderBlock")
    private void minexray$classic(BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices, VertexConsumer consumer,
                                  boolean cull, Random random, Operation<Void> original) {
        if (ClassicXray.skip(state)) {
            return;
        }
        original.call(state, pos, world, matrices, ClassicXray.seeThrough(state) ? ClassicXray.fade(consumer) : consumer, cull, random);
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private void minexray$classicFluid(BlockPos pos, BlockRenderView world, VertexConsumer consumer, BlockState blockState, FluidState fluidState,
                                       CallbackInfo ci) {
        if (ClassicXray.skipFluid(blockState)) {
            ci.cancel();
        }
    }
}
