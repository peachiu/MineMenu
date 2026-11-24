package com.minemenu.mixin;

import com.minemenu.esp.SectionScanner;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A block changed on the client: Ore Vision rescans just that 16x16x16 section. */
@Mixin(WorldRenderer.class)
public class ClientBlockUpdateMixin {
    @Inject(method = "updateBlock", at = @At("TAIL"))
    private void minemenu$blockChanged(BlockView world, BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (oldState != newState) {
            SectionScanner.markDirty(pos);
            com.minemenu.hud.MapColors.markDirty(pos);
        }
    }
}
