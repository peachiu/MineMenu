package com.mineboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;

/** A 10 second FPS benchmark: average FPS, 1% low and the worst frame, posted in chat. */
public final class Benchmark {
    private static final long DURATION_NANOS = 10_000_000_000L;
    private static final int MAX_FRAMES = 20_000;

    private static boolean running;
    private static long start;
    private static long lastFrame;
    private static final long[] frameTimes = new long[MAX_FRAMES];
    private static int frames;

    private Benchmark() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static void start() {
        running = true;
        start = 0; // starts on the first frame after the menu closes
        frames = 0;
    }

    /** Called once per rendered frame (HUD render callback). */
    public static void onFrame(DrawContext context) {
        if (!running) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.nanoTime();
        if (start == 0) {
            start = now;
            lastFrame = now;
            return;
        }
        if (frames < MAX_FRAMES) {
            frameTimes[frames++] = now - lastFrame;
        }
        lastFrame = now;

        long elapsed = now - start;
        if (elapsed >= DURATION_NANOS) {
            running = false;
            report(client, elapsed);
            return;
        }
        int secondsLeft = (int) Math.ceil((DURATION_NANOS - elapsed) / 1e9);
        Text text = Text.literal("MineBoost benchmark: " + secondsLeft + "s, keep playing normally")
                .formatted(Formatting.GREEN);
        context.drawCenteredTextWithShadow(client.textRenderer, text, context.getScaledWindowWidth() / 2, 30, 0xFFFFFF);
    }

    private static void report(MinecraftClient client, long elapsed) {
        if (client.player == null || frames == 0) {
            return;
        }
        long[] sorted = Arrays.copyOf(frameTimes, frames);
        Arrays.sort(sorted);
        int avg = (int) Math.round(frames / (elapsed / 1e9));
        int slowest = Math.max(1, frames / 100);
        long slowTotal = 0;
        for (int i = frames - slowest; i < frames; i++) {
            slowTotal += sorted[i];
        }
        int low = (int) Math.round(slowest * 1e9 / slowTotal);
        int worst = (int) Math.round(1e9 / sorted[frames - 1]);

        String preset = "Custom";
        for (Preset p : Preset.values()) {
            if (p.isActive(client)) {
                preset = p.displayName();
            }
        }
        client.player.sendMessage(Text.literal("MineBoost benchmark: ").formatted(Formatting.GREEN)
                .append(Text.literal("avg " + avg + " FPS").formatted(Formatting.WHITE, Formatting.BOLD))
                .append(Text.literal(", 1% low " + low + ", worst frame " + worst + " FPS").formatted(Formatting.WHITE))
                .append(Text.literal("  (" + preset + ", render distance "
                        + client.options.getViewDistance().getValue() + ")").formatted(Formatting.GRAY)), false);
    }
}
