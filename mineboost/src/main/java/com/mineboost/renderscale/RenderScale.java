package com.mineboost.renderscale;

import com.mineboost.BoostConfig;
import com.mineboost.BoostGuard;
import com.mineboost.Compat;
import com.mineboost.FpsOverlay;
import com.mineboost.mixin.MinecraftClientFramebufferAccessor;
import com.mineboost.mixin.WorldRendererOutlineAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.option.GraphicsMode;
import org.lwjgl.opengl.GL11;

/**
 * Render Scale: the world is drawn into a smaller framebuffer (50-100% of the window) and stretched onto the
 * screen; the HUD, text and menus are drawn afterwards at full resolution, so they stay sharp. On integrated
 * graphics most of a frame is filling pixels, so 75% scale = about 56% of the pixels.
 *
 * <p>How: only for the duration of {@code GameRenderer.renderWorld}, the client's main framebuffer is swapped
 * for ours, so everything the world renderer draws goes there. The glowing-outline effect is reloaded against
 * our framebuffer and sized to it, so outlines still line up. Not available with Iris (shaders) or with
 * Fabulous graphics (its extra framebuffers would need the same treatment).
 */
public final class RenderScale {
    private static SimpleFramebuffer target;
    private static int appliedWidth;
    private static int appliedHeight;
    private static Object appliedOutline;
    private static boolean worldSizedForScale;
    /** Dynamic Render Scale's current value (percent). */
    private static int dynamicScale = 100;
    private static int aboveTargetChecks;
    private static int ticks;

    private RenderScale() {
    }

    /** Why it can't run right now, or null if it can. */
    public static String unavailableReason(MinecraftClient client) {
        if (Compat.IRIS) {
            return "Not available with Iris (shaders have their own resolution settings).";
        }
        if (client.options.getGraphicsMode().getValue() == GraphicsMode.FABULOUS) {
            return "Not available with Fabulous graphics. Use Fancy or Fast.";
        }
        return null;
    }

    /** The scale (percent) used this frame. */
    public static int currentScale() {
        BoostConfig c = BoostConfig.get();
        int max = c.effectiveRenderScale();
        return c.dynamicRenderScale ? Math.min(max, Math.max(c.renderScaleMin, dynamicScale)) : max;
    }

    public static boolean active(MinecraftClient client) {
        return currentScale() < 100 && unavailableReason(client) == null;
    }

    /** Wraps GameRenderer.renderWorld. */
    public static void renderWorld(MinecraftClient client, Runnable renderWorld) {
        if (!active(client)) {
            if (worldSizedForScale) {
                BoostGuard.run("Render Scale", () -> restoreFullSize(client), RenderScale::turnOff);
            }
            renderWorld.run();
            return;
        }
        Framebuffer main = client.getFramebuffer();
        int fullWidth = client.getWindow().getFramebufferWidth();
        int fullHeight = client.getWindow().getFramebufferHeight();
        int scale = currentScale();
        int width = Math.max(1, fullWidth * scale / 100);
        int height = Math.max(1, fullHeight * scale / 100);
        boolean ok = BoostGuard.run("Render Scale", () -> prepare(client, main, width, height), RenderScale::turnOff);
        if (!ok) {
            renderWorld.run();
            return;
        }
        MinecraftClientFramebufferAccessor accessor = (MinecraftClientFramebufferAccessor) client;
        accessor.mineboost$setFramebuffer(target);
        target.beginWrite(true);
        try {
            renderWorld.run();
        } finally {
            accessor.mineboost$setFramebuffer(main);
            main.beginWrite(true);
            target.draw(fullWidth, fullHeight, true);
            main.beginWrite(true);
        }
    }

    /** Makes our framebuffer the right size and points the outline effect at it (only when something changed). */
    private static void prepare(MinecraftClient client, Framebuffer main, int width, int height) {
        int filter = BoostConfig.get().renderScaleNearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        if (target == null) {
            target = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
            target.setClearColor(0, 0, 0, 0);
        } else if (target.textureWidth != width || target.textureHeight != height) {
            target.resize(width, height, MinecraftClient.IS_SYSTEM_MAC);
        }
        target.setTexFilter(filter);
        Object outline = ((WorldRendererOutlineAccessor) client.worldRenderer).mineboost$getOutlineProcessor();
        if (appliedWidth != width || appliedHeight != height || outline != appliedOutline || !worldSizedForScale) {
            MinecraftClientFramebufferAccessor accessor = (MinecraftClientFramebufferAccessor) client;
            accessor.mineboost$setFramebuffer(target);
            try {
                client.worldRenderer.loadEntityOutlinePostProcessor();
                client.worldRenderer.onResized(width, height);
            } finally {
                accessor.mineboost$setFramebuffer(main);
            }
            appliedWidth = width;
            appliedHeight = height;
            appliedOutline = ((WorldRendererOutlineAccessor) client.worldRenderer).mineboost$getOutlineProcessor();
            worldSizedForScale = true;
        }
    }

    /** Back to normal: the outline effect against the real framebuffer, at the window's size. */
    private static void restoreFullSize(MinecraftClient client) {
        worldSizedForScale = false;
        appliedWidth = appliedHeight = 0;
        appliedOutline = null;
        client.worldRenderer.loadEntityOutlinePostProcessor();
        client.worldRenderer.onResized(client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight());
        release();
    }

    /** Frees our framebuffer (leaving a world, turning it off). */
    public static void release() {
        if (target != null) {
            target.delete();
            target = null;
        }
    }

    private static void turnOff() {
        BoostConfig.get().renderScale = 100;
        BoostConfig.get().dynamicRenderScale = false;
        worldSizedForScale = true; // restore on the next frame
    }

    /** Dynamic Render Scale: once a second, a step down below the target, a step up after 3 s well above it. */
    public static void tick(MinecraftClient client) {
        BoostConfig c = BoostConfig.get();
        if (!c.dynamicRenderScale || client.world == null || ++ticks % 20 != 0) {
            if (!c.dynamicRenderScale) {
                dynamicScale = c.effectiveRenderScale();
            }
            return;
        }
        int target = c.fpsTarget > 0 ? c.fpsTarget : 60;
        int avg = FpsOverlay.averageFps(2_000_000_000L);
        if (avg <= 0) {
            return;
        }
        if (avg < target * 0.9) {
            dynamicScale = Math.max(c.renderScaleMin, currentScale() - 5);
            aboveTargetChecks = 0;
        } else if (avg > target * 1.15) {
            if (++aboveTargetChecks >= 3) {
                dynamicScale = Math.min(c.effectiveRenderScale(), currentScale() + 5);
                aboveTargetChecks = 0;
            }
        } else {
            aboveTargetChecks = 0;
        }
    }
}
