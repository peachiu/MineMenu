package com.mineboost;

import net.minecraft.client.MinecraftClient;

/** Idle Mode: after a while without any key or mouse input, lower the FPS until you touch something again. */
public final class Idle {
    private static volatile long lastInput = System.nanoTime();

    private Idle() {
    }

    /** Called on every key press, mouse move, click and scroll. */
    public static void poke() {
        lastInput = System.nanoTime();
    }

    /** The FPS cap Idle Mode wants right now, or 0 for none. */
    public static int cap(MinecraftClient client) {
        BoostConfig c = BoostConfig.get();
        if (c.idleFps <= 0 || client.world == null || Benchmark.isRunning() || AutoPreset.isRunning()) {
            return 0;
        }
        return System.nanoTime() - lastInput > c.idleSeconds * 1_000_000_000L ? c.idleFps : 0;
    }

    public static boolean isIdle(MinecraftClient client) {
        return cap(client) > 0;
    }
}
