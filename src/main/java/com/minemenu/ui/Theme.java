package com.minemenu.ui;

import com.minemenu.core.CheatConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

import java.util.function.BooleanSupplier;

/** Menu themes (dark, light, glass), the accent colour, and the small open animation. */
public final class Theme {
    public static final String[] NAMES = {"dark", "light", "glass"};
    public static final int[] ACCENTS = {0xFF55FF, 0x55FFFF, 0x55FF55, 0xFFAA00, 0xFF5555, 0x5599FF, 0xFFFFFF};
    /** Set by MineMeBoost: true on the Toaster/Potato presets, where animations turn themselves off. */
    public static BooleanSupplier lowEndPreset = () -> false;

    private Theme() {
    }

    public static String name() {
        return CheatConfig.get().theme;
    }

    public static int accent() {
        return CheatConfig.get().accent & 0xFFFFFF;
    }

    /** Draws over the normal menu background: light = a pale veil, glass = see-through, dark = as vanilla. */
    public static void overlay(DrawContext context, int width, int height) {
        switch (name()) {
            case "light" -> context.fill(0, 0, width, height, 0x60E8E8F0);
            case "glass" -> context.fillGradient(0, 0, width, height, 0x20FFFFFF, 0x30000000);
            default -> {
            }
        }
    }

    /** Panel behind a group of buttons. */
    public static void panel(DrawContext context, int x1, int y1, int x2, int y2) {
        int fill = switch (name()) {
            case "light" -> 0x50FFFFFF;
            case "glass" -> 0x28FFFFFF;
            default -> 0x50000000;
        };
        context.fill(x1, y1, x2, y2, fill);
        context.drawBorder(x1, y1, x2 - x1, y2 - y1, 0x60000000 | accent());
    }

    public static boolean animationsOn() {
        return CheatConfig.get().animations && !lowEndPreset.getAsBoolean();
    }

    /** 0..1 over 150 ms since {@code openedAt}; 1 at once when animations are off. */
    public static float openProgress(long openedAt) {
        if (!animationsOn()) {
            return 1f;
        }
        float t = (Util.getMeasuringTimeMs() - openedAt) / 150f;
        return Math.min(1f, Math.max(0f, t));
    }
}
