package com.minexray.screen;

import com.minexray.XrayConfig;
import com.minexray.hud.SessionStats;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** HUD: which of MineXray's HUD pieces show, and the Session Stats reset. */
final class HudPage implements XrayPage {
    @Override
    public void init(XrayScreen screen, int x, int y, int width, int height) {
        XrayConfig c = XrayConfig.get();
        int w = Math.min(220, width);
        screen.add(Widgets.toggle("minexray.hud.indicator", () -> c.hudIndicator, v -> c.hudIndicator = v, x, y, w, null));
        screen.add(Widgets.toggle("minexray.hud.ore_counter", () -> c.hudCounter, v -> c.hudCounter = v, x, y + 22, w, null));
        screen.add(Widgets.toggle("minexray.hud.arrow", () -> c.hudArrow, v -> c.hudArrow = v, x, y + 44, w, null));
        screen.add(Widgets.toggle("minexray.hud.y_level", () -> c.hudYGuide, v -> c.hudYGuide = v, x, y + 66, w, null));
        screen.add(Widgets.toggle("minexray.hud.session", () -> c.hudStats, v -> c.hudStats = v, x, y + 88, w, null));
        screen.add(Widgets.toggle("minexray.hud.minimap", () -> c.minimapLayer, v -> c.minimapLayer = v, x, y + 110, w, null));
        screen.add(ButtonWidget.builder(Text.translatable("minexray.hud.reset_stats"), b -> SessionStats.reset()).dimensions(x, y + 138, w, 20).build());
    }
}
