package com.minexray.mode;

import com.minemenu.player.PlayerModules;
import com.minexray.BlockFilter;
import com.minexray.XrayConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;

/**
 * Classic X-Ray: the chunk builder skips every block that isn't on the preset's "Visible" list (or draws it
 * see-through, at the chosen opacity), with full brightness and no smooth-lighting shadows while it's on.
 * Chunks are rebuilt once when it's switched or the list changes, never in a loop. Everything is put back when
 * it's switched off. Not available with Sodium (see SodiumCompat).
 */
public final class ClassicXray {
    /** Read by the chunk-builder mixins (on the chunk builder threads). */
    private static volatile boolean active;
    private static volatile int opacity;
    private static volatile boolean fluids;
    private static boolean savedCulling = true;
    private static boolean reloadRequested;
    private static int reloadDelay;
    private static long rebuildingSince;

    private ClassicXray() {
    }

    public static boolean active() {
        return active;
    }

    /** True: this block isn't drawn at all (hidden, and opacity 0). */
    public static boolean skip(BlockState state) {
        return active && opacity == 0 && !state.isAir() && !BlockFilter.get().isVisible(state);
    }

    /** True: this block is drawn see-through (hidden, opacity above 0). */
    public static boolean seeThrough(BlockState state) {
        return active && opacity > 0 && !state.isAir() && !BlockFilter.get().isVisible(state);
    }

    public static boolean skipFluid(BlockState state) {
        return active && !fluids && !BlockFilter.get().isVisible(state);
    }

    /** Opacity as an alpha factor (0-1). */
    public static float alpha() {
        return opacity / 100f;
    }

    /** True while the chunks are still being rebuilt after a switch (the HUD says "Rebuilding chunks..."). */
    public static boolean rebuilding() {
        return rebuildingSince != 0;
    }

    public static void requestReload() {
        if (active) {
            reloadRequested = true;
            reloadDelay = 10; // half a second, so dragging a slider or ticking boxes rebuilds once
        }
    }

    public static void tick(MinecraftClient client, boolean wanted) {
        XrayConfig config = XrayConfig.get();
        if (wanted != active) {
            if (wanted) {
                savedCulling = client.chunkCullingEnabled;
                client.chunkCullingEnabled = false; // hidden blocks no longer hide what's behind them
                opacity = config.classicOpacity;
                fluids = config.classicFluids;
            } else {
                client.chunkCullingEnabled = savedCulling;
            }
            active = wanted;
            PlayerModules.forceFullBright = wanted;
            reload(client);
        } else if (active && (opacity != config.classicOpacity || fluids != config.classicFluids)) {
            opacity = config.classicOpacity;
            fluids = config.classicFluids;
            reloadRequested = true;
            reloadDelay = 10;
        }
        if (reloadRequested && --reloadDelay <= 0) {
            reloadRequested = false;
            reload(client);
        }
        if (rebuildingSince != 0 && client.worldRenderer != null
                && (client.worldRenderer.isTerrainRenderComplete() || System.currentTimeMillis() - rebuildingSince > 15_000)) {
            rebuildingSince = 0;
        }
    }

    private static void reload(MinecraftClient client) {
        if (client.worldRenderer != null && client.world != null) {
            client.worldRenderer.reload();
            rebuildingSince = System.currentTimeMillis();
        }
    }

    /** Wraps the chunk builder's buffer so a see-through block's vertices get the chosen opacity. */
    public static VertexConsumer fade(VertexConsumer inner) {
        return new FadingConsumer(inner, alpha());
    }

    private record FadingConsumer(VertexConsumer inner, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            inner.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int a) {
            inner.color(red, green, blue, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            inner.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            inner.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            inner.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            inner.normal(x, y, z);
            return this;
        }

        @Override
        public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            int a = Math.round(((color >>> 24) & 0xFF) * alpha);
            inner.vertex(x, y, z, (a << 24) | (color & 0xFFFFFF), u, v, overlay, light, nx, ny, nz);
        }
    }
}
