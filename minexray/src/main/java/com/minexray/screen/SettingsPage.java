package com.minexray.screen;

import com.minexray.MineXrayClient;
import com.minexray.XrayConfig;
import com.minexray.XrayHost;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Settings: performance (Adaptive range, memory limit), the cost test, keys, and reset. */
final class SettingsPage implements XrayPage {
    private static boolean confirmReset;
    private static boolean confirmServers;
    private int x;
    private int infoY;

    @Override
    public void init(XrayScreen screen, int x, int y, int width, int height) {
        this.x = x;
        XrayConfig c = XrayConfig.get();
        int w = Math.min(220, (width - 6) / 2);
        screen.add(Widgets.toggle("minexray.settings.adaptive", () -> c.adaptiveRange, v -> c.adaptiveRange = v, x, y, w, null));
        screen.add(Widgets.slider("minexray.settings.memory", 16, 256, 16, () -> c.memoryLimitMb, v -> c.memoryLimitMb = v, x + w + 6, y, w, "minexray.unit.mb", null));
        ButtonWidget cost = screen.add(ButtonWidget.builder(Text.translatable("minexray.settings.cost_test"), b -> {
            screen.close();
            XrayHost.costTest.accept(screen.mc());
        }).dimensions(x, y + 22, w, 20).tooltip(Tooltip.of(Text.translatable("minexray.settings.cost_test.tooltip"))).build());
        cost.active = XrayHost.costTest != null && MineXrayClient.allowed() && !XrayHost.costTestRunning.getAsBoolean();
        if (XrayHost.costTest == null) {
            cost.setTooltip(Tooltip.of(Text.translatable("minexray.settings.cost_test.needs_mineboost")));
        }
        screen.add(ButtonWidget.builder(Text.translatable("minexray.settings.keys"), b -> screen.mc().setScreen(new KeybindsScreen(screen, screen.mc().options)))
                .dimensions(x + w + 6, y + 22, w, 20).build());
        screen.add(ButtonWidget.builder(Text.translatable(confirmReset ? "minexray.settings.reset_confirm" : "minexray.settings.reset").formatted(Formatting.RED), b -> {
            if (!confirmReset) {
                confirmReset = true;
            } else {
                confirmReset = false;
                XrayConfig.resetToDefaults();
                com.minexray.BlockFilter.rebuild();
            }
            screen.rebuild();
        }).dimensions(x, y + 44, w, 20).build());
        // 4.0: MineXray on multiplayer servers, only if you switch it on (two clicks, with a warning).
        Text serversLabel = confirmServers ? Text.translatable("minexray.settings.servers_confirm").formatted(Formatting.GOLD)
                : Text.translatable("minexray.settings.servers").append(": ").append(Widgets.onOff(c.allowMultiplayer));
        screen.add(ButtonWidget.builder(serversLabel, b -> {
            if (c.allowMultiplayer) {
                c.allowMultiplayer = false;
            } else if (!confirmServers) {
                confirmServers = true;
            } else {
                confirmServers = false;
                c.allowMultiplayer = true;
            }
            XrayConfig.markDirty();
            screen.rebuild();
        }).dimensions(x + w + 6, y + 44, w, 20).tooltip(Tooltip.of(Text.translatable("minexray.settings.servers.tooltip"))).build());
        infoY = y + 74;
    }

    @Override
    public void render(XrayScreen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        var text = screen.font();
        context.drawTextWithShadow(text, Text.translatable("minexray.settings.adaptive.info").formatted(Formatting.GRAY), x, infoY, 0xFFFFFF);
        context.drawTextWithShadow(text, Text.translatable("minexray.settings.safe_mode").formatted(Formatting.GRAY), x, infoY + 12, 0xFFFFFF);
        context.drawTextWithShadow(text, Text.translatable("minexray.settings.files").formatted(Formatting.DARK_GRAY), x, infoY + 24, 0xFFFFFF);
        int wrap = screen.width - x - 10;
        int y = infoY + 40;
        for (var line : text.wrapLines(Text.translatable("minexray.settings.servers.info").formatted(XrayConfig.get().allowMultiplayer ? Formatting.GOLD : Formatting.GRAY), wrap)) {
            context.drawTextWithShadow(text, line, x, y, 0xFFFFFF);
            y += 10;
        }
    }
}
