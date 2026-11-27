package com.minexray.screen;

import com.minexray.XrayHost;
import com.minexray.MineXrayClient;
import com.minexray.PresetManager;
import com.minexray.XrayConfig;
import com.minexray.XrayKeys;
import com.minexray.XrayMode;
import com.minexray.XrayPreset;
import com.minexray.compat.SodiumCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Overview: every mode with its key, the preset in use, and what MineXray is doing right now. */
final class OverviewPage implements XrayPage {
    private int x;
    private int infoY;

    @Override
    public void init(XrayScreen screen, int x, int y, int width, int height) {
        this.x = x;
        int column = Math.min(200, (width - 6) / 2);
        int row = 0;
        for (XrayMode mode : XrayMode.values()) {
            int bx = x + (row % 2) * (column + 6);
            int by = y + (row / 2) * 22;
            ButtonWidget button = ButtonWidget.builder(label(mode), b -> {
                XrayKeys.toggle(screen.mc(), mode);
                b.setMessage(label(mode));
            }).dimensions(bx, by, column, 20).build();
            String key = XrayKeys.of(mode).getBoundKeyLocalizedText().getString();
            Text tip = Text.literal(mode.description() + "\n").append(Text.translatable("minexray.overview.key", key).formatted(Formatting.GRAY));
            if (mode == XrayMode.CLASSIC && SodiumCompat.present()) {
                tip = Text.translatable("minexray.classic.sodium");
                button.active = false;
            }
            button.setTooltip(Tooltip.of(tip));
            screen.add(button);
            row++;
        }
        int py = y + ((row + 1) / 2) * 22 + 6;
        List<XrayPreset> presets = PresetManager.all();
        screen.add(ButtonWidget.builder(presetLabel(), b -> {
            PresetManager.next();
            b.setMessage(presetLabel());
        }).dimensions(x, py, column, 20).tooltip(Tooltip.of(Text.translatable("minexray.overview.preset.tooltip", presets.size()))).build());
        screen.add(ButtonWidget.builder(Text.translatable("minexray.overview.scan"), b -> screen.show(XrayScreen.Page.SCANNER))
                .dimensions(x + column + 6, py, column, 20).build());
        infoY = py + 28;
    }

    private static Text label(XrayMode mode) {
        return Text.literal(mode.displayName() + ": ").append(Widgets.onOff(XrayConfig.get().isOn(mode)));
    }

    private static Text presetLabel() {
        return Text.translatable("minexray.overview.preset").append(": ").append(Text.literal(PresetManager.active().name).formatted(Formatting.YELLOW));
    }

    @Override
    public void render(XrayScreen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        var text = screen.mc().textRenderer;
        int y = infoY;
        if (SodiumCompat.present()) {
            context.drawTextWithShadow(text, Text.translatable("minexray.classic.sodium").formatted(Formatting.YELLOW), x, y, 0xFFFFFF);
            y += 12;
        }
        context.drawTextWithShadow(text, Text.translatable("minexray.overview.help").formatted(Formatting.GRAY), x, y, 0xFFFFFF);
        y += 12;
        context.drawTextWithShadow(text, Text.literal(XrayHost.tr("minexray.overview.status", "Status") + ": " + MineXrayClient.debugLine())
                .formatted(Formatting.DARK_GRAY), x, y, 0xFFFFFF);
        if (MineXrayClient.rangeFactor() < 1.0) {
            y += 12;
            context.drawTextWithShadow(text, Text.translatable("minexray.overview.adaptive", Math.round(MineXrayClient.rangeFactor() * 100))
                    .formatted(Formatting.GOLD), x, y, 0xFFFFFF);
        }
    }
}
