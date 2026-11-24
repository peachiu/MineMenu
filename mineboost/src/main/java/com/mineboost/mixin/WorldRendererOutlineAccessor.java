package com.mineboost.mixin;

import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Render Scale: notices when the game reloads the glowing-outline effect (resource reload), to resize it again. */
@Mixin(WorldRenderer.class)
public interface WorldRendererOutlineAccessor {
    @Accessor("entityOutlinePostProcessor")
    PostEffectProcessor mineboost$getOutlineProcessor();
}
