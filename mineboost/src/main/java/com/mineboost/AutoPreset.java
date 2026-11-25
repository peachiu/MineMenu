package com.mineboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Auto Preset: tries the presets from Ultra down, measures your FPS with each one, and keeps the best
 * looking one that reaches your FPS target (the FPS Target setting, or 60 if that's off).
 */
public final class AutoPreset {
    private static final long SETTLE_NANOS = 3_000_000_000L;   // chunks reload after a preset change
    private static final long MEASURE_NANOS = 4_000_000_000L;

    private static boolean running;
    private static int index;           // into Preset.values(), counting down from the best looking
    private static long phaseStart;
    private static boolean measuring;
    private static int frames;
    private static int target;

    private AutoPreset() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static void start(MinecraftClient client) {
        int fpsTarget = BoostConfig.get().fpsTarget;
        target = fpsTarget > 0 ? fpsTarget : 60;
        index = Preset.values().length - 1; // Ultra
        running = true;
        beginPreset(client);
    }

    private static void beginPreset(MinecraftClient client) {
        Preset.values()[index].apply(client);
        phaseStart = System.nanoTime();
        measuring = false;
        frames = 0;
    }

    /** Called once per rendered frame (HUD render callback). */
    public static void onFrame(DrawContext context) {
        if (!running) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            running = false;
            return;
        }
        long now = System.nanoTime();
        Preset preset = Preset.values()[index];
        if (!measuring && now - phaseStart >= SETTLE_NANOS) {
            measuring = true;
            phaseStart = now;
            frames = 0;
        } else if (measuring) {
            frames++;
            if (now - phaseStart >= MEASURE_NANOS) {
                int avg = (int) Math.round(frames / ((now - phaseStart) / 1e9));
                if (avg >= target || index == 0) {
                    finish(client, preset, avg);
                } else {
                    index--;
                    beginPreset(client);
                }
                return;
            }
        }
        Text text = Text.translatable("mineboost.text.mineboost_testing").formatted(Formatting.GREEN)
                .append(Text.literal(preset.displayName()).formatted(preset.color()))
                .append(Text.translatable("mineboost.text.target_s_fps_keep_playing_normally", target).formatted(Formatting.GREEN));
        context.drawCenteredTextWithShadow(client.textRenderer, text, context.getScaledWindowWidth() / 2, 30, 0xFFFFFF);
    }

    private static void finish(MinecraftClient client, Preset preset, int avg) {
        running = false;
        if (client.player == null) {
            return;
        }
        boolean reached = avg >= target;
        client.player.sendMessage(Text.translatable("mineboost.text.mineboost_auto_preset").formatted(Formatting.GREEN)
                .append(Text.literal(preset.displayName()).formatted(preset.color(), Formatting.BOLD))
                .append((reached ? Text.translatable("mineboost.text.s_fps_target_s", avg, target) : Text.translatable("mineboost.text.is_the_fastest_but_only_reached", avg, target)).formatted(Formatting.WHITE)), false);
    }
}
