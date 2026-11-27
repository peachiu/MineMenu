package com.minexray.mixin;

import com.minexray.MineXrayClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A block changed on the client: MineXray scans that section again (ESP updates within a second). */
@Mixin(WorldRenderer.class)
public class BlockUpdateMixin {
    @Inject(method = "updateBlock", at = @At("TAIL"))
    private void minexray$blockChanged(BlockView world, BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (oldState != newState) {
            MineXrayClient.blockChanged(pos);
        }
    }
}
