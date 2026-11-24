package com.mineboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;

/** Top-right FPS counter with average FPS and 1% low over the last few seconds. */
public final class FpsOverlay {
    private static final int SAMPLES = 2000;
    private static final long WINDOW_NANOS = 5_000_000_000L; // stats over the last 5 seconds
    private static final long REFRESH_NANOS = 500_000_000L;  // update the numbers twice a second

    private static final long[] frameTimes = new long[SAMPLES];
    private static final long[] frameEnds = new long[SAMPLES];
    private static int next;
    private static int count;
    private static long lastFrame;
    private static long lastRefresh;
    private static int avgFps;
    private static int lowFps;

    private FpsOverlay() {
    }

    /** Called once per rendered frame (from the HUD render callback). */
    public static void render(DrawContext context) {
        long now = System.nanoTime();
        if (lastFrame != 0) {
            frameTimes[next] = now - lastFrame;
            frameEnds[next] = now;
            next = (next + 1) % SAMPLES;
            count = Math.min(count + 1, SAMPLES);
        }
        lastFrame = now;

        MinecraftClient client = MinecraftClient.getInstance();
        if (!BoostConfig.get().fpsOverlay || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
            return;
        }
        if (now - lastRefresh > REFRESH_NANOS) {
            refreshStats(now);
            lastRefresh = now;
        }

        int fps = client.getCurrentFps();
        Text line = Text.empty()
                .append(Text.literal(fps + " FPS").formatted(color(fps), Formatting.BOLD))
                .append(Text.literal("  avg ").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(avgFps)).formatted(color(avgFps)))
                .append(Text.literal("  1% low ").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(lowFps)).formatted(color(lowFps)));
        if (BoostConfig.get().fpsTarget > 0) {
            line = line.copy().append(Text.literal("  RD " + client.options.getViewDistance().getValue())
                    .formatted(Formatting.AQUA));
        }

        int width = client.textRenderer.getWidth(line);
        int x = context.getScaledWindowWidth() - width - 4;
        int y = 4 + effectRowsHeight(client);
        context.fill(x - 3, y - 3, x + width + 3, y + 11, 0x90000000);
        context.drawTextWithShadow(client.textRenderer, line, x, y, 0xFFFFFF);
        if (BoostConfig.get().frameGraph) {
            drawGraph(context, context.getScaledWindowWidth() - 4, y + 13);
        }
    }

    /** Frame-time graph: the last 100 frames, one bar each (green under 16.7 ms, yellow under 33 ms, red above). */
    private static void drawGraph(DrawContext context, int right, int top) {
        int bars = Math.min(100, count);
        int height = 24;
        context.fill(right - 100, top, right, top + height, 0x90000000);
        for (int i = 0; i < bars; i++) {
            int idx = Math.floorMod(next - 1 - i, SAMPLES);
            double ms = frameTimes[idx] / 1e6;
            int h = (int) Math.min(height, Math.max(1, ms * height / 50.0));
            int color = ms <= 16.7 ? 0xFF55FF55 : ms <= 33.4 ? 0xFFFFFF55 : 0xFFFF5555;
            int x = right - 1 - i;
            context.fill(x, top + height - h, x + 1, top + height, color);
        }
    }

    /** Average FPS over the last {@code windowNanos}, or 0 if there aren't enough frames yet. */
    public static int averageFps(long windowNanos) {
        long now = System.nanoTime();
        int n = 0;
        long total = 0;
        for (int i = 0; i < count; i++) {
            int idx = Math.floorMod(next - 1 - i, SAMPLES);
            if (now - frameEnds[idx] > windowNanos) {
                break;
            }
            n++;
            total += frameTimes[idx];
        }
        // Need most of the window covered, or the number means nothing (e.g. just unpaused).
        if (n < 10 || total < windowNanos / 2) {
            return 0;
        }
        return (int) Math.round(n * 1e9 / total);
    }

    private static void refreshStats(long now) {
        long[] recent = new long[count];
        int n = 0;
        long total = 0;
        for (int i = 0; i < count; i++) {
            int idx = Math.floorMod(next - 1 - i, SAMPLES);
            if (now - frameEnds[idx] > WINDOW_NANOS) {
                break;
            }
            recent[n++] = frameTimes[idx];
            total += frameTimes[idx];
        }
        if (n == 0 || total == 0) {
            avgFps = lowFps = 0;
            return;
        }
        avgFps = (int) Math.round(n * 1e9 / total);
        // 1% low = the FPS of the slowest 1% of frames.
        Arrays.sort(recent, 0, n);
        int slowest = Math.max(1, n / 100);
        long slowTotal = 0;
        for (int i = n - slowest; i < n; i++) {
            slowTotal += recent[i];
        }
        lowFps = (int) Math.round(slowest * 1e9 / slowTotal);
    }

    /** Status effect icons also live in the top-right corner; go below them. */
    private static int effectRowsHeight(MinecraftClient client) {
        if (client.player == null) {
            return 0;
        }
        boolean beneficial = false;
        boolean harmful = false;
        for (StatusEffectInstance effect : client.player.getStatusEffects()) {
            if (!effect.shouldShowIcon()) {
                continue;
            }
            if (effect.getEffectType().value().isBeneficial()) {
                beneficial = true;
            } else {
                harmful = true;
            }
        }
        return (beneficial ? 26 : 0) + (harmful ? 26 : 0);
    }

    private static Formatting color(int fps) {
        return fps >= 60 ? Formatting.GREEN : fps >= 30 ? Formatting.YELLOW : Formatting.RED;
    }
}
