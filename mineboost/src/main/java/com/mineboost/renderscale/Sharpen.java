package com.mineboost.renderscale;

import com.mineboost.BoostConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Sharpen for Render Scale: the smaller world image is stretched to the window with a Contrast Adaptive
 * Sharpening shader (assets/mineboost/shaders/core/cas.fsh) instead of a plain copy, in the same single pass.
 * It makes 75% look close to 100%. Costs 9 texture reads per screen pixel, only while Render Scale is below 100%.
 * If the shader failed to load (or Sharpen is Off), the plain copy is used.
 */
public final class Sharpen {
    private static ShaderProgram program;

    private Sharpen() {
    }

    /** Called once at start-up: loads the shader with the other core shaders (and again on resource reload). */
    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context ->
                context.register(Identifier.of("mineboost", "cas"), VertexFormats.BLIT_SCREEN, loaded -> program = loaded));
    }

    public static boolean available() {
        return program != null;
    }

    /** Draws {@code source} over the whole window (like {@link Framebuffer#draw}), sharpened if Sharpen is on. */
    public static void drawScaled(Framebuffer source, int windowWidth, int windowHeight) {
        int sharpen = BoostConfig.get().renderScaleSharpen;
        if (program == null || sharpen <= 0) {
            source.draw(windowWidth, windowHeight, true);
            return;
        }
        RenderSystem.assertOnRenderThread();
        GlStateManager._colorMask(true, true, true, false);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._viewport(0, 0, windowWidth, windowHeight);
        GlStateManager._disableBlend();
        program.addSampler("DiffuseSampler", source.getColorAttachment());
        GlUniform inSize = program.getUniform("InSize");
        if (inSize != null) {
            inSize.set((float) source.textureWidth, (float) source.textureHeight);
        }
        GlUniform sharpness = program.getUniform("Sharpness");
        if (sharpness != null) {
            sharpness.set(sharpen / 100f);
        }
        program.bind();
        BufferBuilder buffer = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.BLIT_SCREEN);
        buffer.vertex(0.0F, 0.0F, 0.0F);
        buffer.vertex(1.0F, 0.0F, 0.0F);
        buffer.vertex(1.0F, 1.0F, 0.0F);
        buffer.vertex(0.0F, 1.0F, 0.0F);
        BufferRenderer.draw(buffer.end());
        program.unbind();
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
    }
}
