package com.minemeboost;

import com.mineboost.BoostScreen;
import com.minemenu.MineMenuClient;
import com.minemenu.MineMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** The three tabs at the top of the menu (MineMenu, MineBoost, MineXray), and which one Right Shift opens. */
public final class Hub {
    public enum Tab { MINEMENU, MINEBOOST, MINEXRAY }

    private static final int TAB_WIDTH = 110;

    /** Right Shift reopens whichever tab you used last. */
    private static Tab lastTab = Tab.MINEMENU;

    private Hub() {
    }

    /** Right Shift: open the last tab (on servers MineMenu only offers the fair Utility hacks). */
    public static void openLastTab() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) {
            return;
        }
        open(lastTab);
    }

    public static void open(Tab tab) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Leaving MineBoost: apply sliders still being dragged, like its Done button does.
        if (client.currentScreen instanceof BoostScreen boostScreen) {
            boostScreen.applyPendingValues();
        }
        lastTab = tab;
        client.setScreen(switch (tab) {
            case MINEMENU -> new MineMenuScreen();
            case MINEBOOST -> new BoostScreen(null, client.options);
            case MINEXRAY -> new com.minexray.screen.XrayScreen(null);
        });
    }

    /** Builds the tab row. The open tab is shown pressed (disabled). */
    public static List<ButtonWidget> tabButtons(Tab current) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean singleplayer = MineMenuClient.isSingleplayer(client);

        ButtonWidget menuTab = ButtonWidget.builder(
                        Text.translatable("minemeboost.text.minemenu").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                        b -> open(Tab.MINEMENU))
                .width(TAB_WIDTH)
                .tooltip(Tooltip.of(singleplayer ? Text.translatable("minemeboost.text.fun_cheats_fly_god_mode_spawn") : Text.translatable("minemeboost.text.cheats_only_work_in_singleplayer_on")))
                .build();
        menuTab.active = current != Tab.MINEMENU;

        ButtonWidget boostTab = ButtonWidget.builder(
                        Text.translatable("minemeboost.text.mineboost").formatted(Formatting.GREEN, Formatting.BOLD),
                        b -> open(Tab.MINEBOOST))
                .width(TAB_WIDTH)
                .tooltip(Tooltip.of(Text.translatable("minemeboost.text.fps_boost_presets_graphics_settings_and")))
                .build();
        boostTab.active = current != Tab.MINEBOOST;

        ButtonWidget xrayTab = ButtonWidget.builder(
                        Text.translatable("minemeboost.text.minexray").formatted(Formatting.AQUA, Formatting.BOLD),
                        b -> open(Tab.MINEXRAY))
                .width(TAB_WIDTH)
                .tooltip(Tooltip.of(singleplayer || com.minexray.XrayConfig.get().allowMultiplayer ? Text.translatable("minemeboost.text.minexray.tooltip")
                        : Text.translatable("minemeboost.text.minexray.servers")))
                .build();
        xrayTab.active = current != Tab.MINEXRAY;

        return List.of(menuTab, boostTab, xrayTab);
    }
}
