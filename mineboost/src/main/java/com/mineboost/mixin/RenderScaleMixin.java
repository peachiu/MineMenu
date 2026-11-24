package com.mineboost.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mineboost.renderscale.RenderScale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Render Scale: wraps the world rendering inside GameRenderer.render (not the HUD). Not loaded when Iris is
 * installed (see MineBoostMixinPlugin): shaders replace this whole path.
 */
@Mixin(GameRenderer.class)
public class RenderScaleMixin {
    @Shadow
    @Final
    MinecraftClient client;

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V"))
    private void mineboost$renderScaled(GameRenderer self, RenderTickCounter tickCounter, Operation<Void> original) {
        RenderScale.renderWorld(client, () -> original.call(self, tickCounter));
    }
}
