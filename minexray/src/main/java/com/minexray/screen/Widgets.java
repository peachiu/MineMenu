package com.minexray.screen;

import com.minexray.XrayHost;
import com.minexray.XrayConfig;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Buttons and sliders for MineXray's pages; every change is saved (and applied) at once. */
public final class Widgets {
    /** Colours the colour buttons cycle through. */
    public static final int[] PALETTE = {0x5DECF5, 0x17DD62, 0xFCEE4B, 0xFF2828, 0x2E5BFF, 0xE77C56, 0xD8AF93, 0xD2693C, 0xF2EBE3,
            0xFFAA00, 0xFF55FF, 0xAA44FF, 0x55FFFF, 0xFFFFFF, 0x606060};

    private Widgets() {
    }

    public static Text onOff(boolean on) {
        return Text.translatable(on ? "minexray.on" : "minexray.off").formatted(on ? Formatting.GREEN : Formatting.RED);
    }

    public static ButtonWidget toggle(String key, BooleanSupplier get, Consumer<Boolean> set, int x, int y, int w, Runnable after) {
        ButtonWidget[] holder = new ButtonWidget[1];
        holder[0] = ButtonWidget.builder(label(key, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            XrayConfig.markDirty();
            b.setMessage(label(key, get.getAsBoolean()));
            if (after != null) {
                after.run();
            }
        }).dimensions(x, y, w, 20).build();
        String tip = XrayHost.tr(key + ".tooltip", "");
        if (!tip.isEmpty()) {
            holder[0].setTooltip(Tooltip.of(Text.literal(tip)));
        }
        return holder[0];
    }

    private static Text label(String key, boolean on) {
        return Text.translatable(key).append(": ").append(onOff(on));
    }

    /** Cycles through {@code values} (shown with {@code key + "." + value.toLowerCase()}). */
    public static ButtonWidget cycle(String key, String[] values, java.util.function.Supplier<String> get, Consumer<String> set, int x, int y, int w,
                                     Runnable after) {
        return ButtonWidget.builder(cycleLabel(key, get.get()), b -> {
            int i = java.util.Arrays.asList(values).indexOf(get.get());
            set.accept(values[(i + 1) % values.length]);
            XrayConfig.markDirty();
            b.setMessage(cycleLabel(key, get.get()));
            if (after != null) {
                after.run();
            }
        }).dimensions(x, y, w, 20).build();
    }

    private static Text cycleLabel(String key, String value) {
        return Text.translatable(key).append(": ").append(Text.translatable(key + "." + value.toLowerCase(java.util.Locale.ROOT)).formatted(Formatting.YELLOW));
    }

    /** A colour square that steps through {@link #PALETTE}. */
    public static ButtonWidget color(IntSupplier get, IntConsumer set, int x, int y, int size, Runnable after) {
        return new ButtonWidget(x, y, size, 20, Text.literal("■").withColor(get.getAsInt()), b -> {
            int current = get.getAsInt();
            int i = 0;
            for (int k = 0; k < PALETTE.length; k++) {
                if (PALETTE[k] == current) {
                    i = k + 1;
                }
            }
            set.accept(PALETTE[i % PALETTE.length]);
            b.setMessage(Text.literal("■").withColor(get.getAsInt()));
            if (after != null) {
                after.run();
            }
        }, s -> s.get()) {
        };
    }

    public static SliderWidget slider(String key, int min, int max, int step, IntSupplier get, IntConsumer set, int x, int y, int w, String unitKey,
                                      Runnable after) {
        return new SliderWidget(x, y, w, 20, Text.empty(), (get.getAsInt() - min) / (double) (max - min)) {
            {
                updateMessage();
            }

            private int current() {
                int raw = min + (int) Math.round(value * (max - min));
                return Math.max(min, Math.min(max, Math.round(raw / (float) step) * step));
            }

            @Override
            protected void updateMessage() {
                MutableText text = Text.translatable(key).append(": ").append(Text.literal(String.valueOf(current())).formatted(Formatting.YELLOW));
                if (unitKey != null) {
                    text.append(" ").append(Text.translatable(unitKey));
                }
                setMessage(text);
            }

            @Override
            protected void applyValue() {
                if (current() != get.getAsInt()) {
                    set.accept(current());
                    XrayConfig.markDirty();
                    if (after != null) {
                        after.run();
                    }
                }
            }
        };
    }
}
