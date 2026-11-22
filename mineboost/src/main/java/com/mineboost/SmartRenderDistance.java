package com.mineboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Smart Render Distance: lowers the render distance when FPS drops below your target, and
 * raises it again (up to the distance you picked) when there's FPS to spare.
 * Render distance is by far the setting with the biggest effect on FPS.
 */
public final class SmartRenderDistance {
    private static final int MIN_RENDER_DISTANCE = 4;
    /** After a change, chunks reload and FPS dips for a moment: wait before judging again. */
    private static final int SETTLE_TICKS = 100;
    private static final long MEASURE_NANOS = 3_000_000_000L;

    private static int cooldown = SETTLE_TICKS;
    private static int lastChange; // -1 lowered, +1 raised, 0 none

    private SmartRenderDistance() {
    }

    /** Called every client tick. */
    public static void tick(MinecraftClient client) {
        BoostConfig config = BoostConfig.get();
        int target = config.fpsTarget;
        // Only judge FPS during normal play: menus, loading and alt-tab all distort it.
        if (target <= 0 || client.world == null || client.player == null || client.currentScreen != null
                || !client.isWindowFocused()) {
            cooldown = Math.max(cooldown, 40);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = 20; // check once a second

        // A Max Framerate below the target can never be reached; aim just under the cap instead.
        int maxFps = client.options.getMaxFps().getValue();
        if (maxFps < 260) {
            target = Math.min(target, maxFps - 2);
        }

        int avg = FpsOverlay.averageFps(MEASURE_NANOS);
        if (avg <= 0) {
            return;
        }
        SimpleOption<Integer> renderDistance = client.options.getViewDistance();
        int current = renderDistance.getValue();
        int max = Math.max(MIN_RENDER_DISTANCE, config.autoRenderMax);

        if (avg < target * 0.9 && current > MIN_RENDER_DISTANCE) {
            // Far below target: drop faster.
            int step = avg < target * 0.6 ? 2 : 1;
            set(renderDistance, Math.max(MIN_RENDER_DISTANCE, current - step));
            lastChange = -1;
        } else if (avg > target * 1.3 && current < max && lastChange != -1) {
            set(renderDistance, current + 1);
            lastChange = 1;
        } else if (avg > target * 1.6 && current < max) {
            // Lots of headroom even right after a drop: try going back up.
            set(renderDistance, current + 1);
            lastChange = 1;
        } else {
            lastChange = 0;
        }
    }

    private static void set(SimpleOption<Integer> renderDistance, int value) {
        renderDistance.setValue(value); // vanilla schedules the terrain reload
        cooldown = SETTLE_TICKS;
    }

    /** Smart Render Distance was just turned on, or a preset applied: start from this distance. */
    public static void restartFrom(int renderDistance) {
        BoostConfig.get().autoRenderMax = renderDistance;
        cooldown = SETTLE_TICKS;
        lastChange = 0;
    }
}
