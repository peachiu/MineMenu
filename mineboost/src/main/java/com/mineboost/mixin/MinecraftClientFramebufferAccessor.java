package com.mineboost.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Render Scale swaps the main framebuffer while the world is drawn (the field is final in vanilla). */
@Mixin(MinecraftClient.class)
public interface MinecraftClientFramebufferAccessor {
    @Mutable
    @Accessor("framebuffer")
    void mineboost$setFramebuffer(Framebuffer framebuffer);
}
