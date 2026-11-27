package com.minexray.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.minexray.mode.ClassicXray;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.SectionBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Classic X-Ray: a hidden block counts as invisible while a chunk is built, so neither Minecraft's block renderer
 * nor Fabric's (Indigo, which draws every block by default) draws it.
 */
@Mixin(SectionBuilder.class)
public class SectionBuilderMixin {
    @WrapOperation(method = "build", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getRenderType()Lnet/minecraft/block/BlockRenderType;"))
    private BlockRenderType minexray$hidden(BlockState state, Operation<BlockRenderType> original) {
        return ClassicXray.skip(state) ? BlockRenderType.INVISIBLE : original.call(state);
    }
}
