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

/** The two tabs at the top of the menu, and which one Right Shift opens. */
public final class Hub {
    public enum Tab { MINEMENU, MINEBOOST }

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
        if (lastTab == Tab.MINEMENU) {
            open(Tab.MINEMENU);
        } else {
            open(Tab.MINEBOOST);
        }
    }

    public static void open(Tab tab) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Leaving MineBoost: apply sliders still being dragged, like its Done button does.
        if (client.currentScreen instanceof BoostScreen boostScreen) {
            boostScreen.applyPendingValues();
        }
        lastTab = tab;
        client.setScreen(tab == Tab.MINEMENU ? new MineMenuScreen() : new BoostScreen(null, client.options));
    }

    /** Builds the tab row. The open tab is shown pressed (disabled). */
    public static List<ButtonWidget> tabButtons(Tab current) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean singleplayer = MineMenuClient.isSingleplayer(client);

        ButtonWidget menuTab = ButtonWidget.builder(
                        Text.literal("MineMenu").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                        b -> open(Tab.MINEMENU))
                .width(TAB_WIDTH)
                .tooltip(Tooltip.of(Text.literal(singleplayer
                        ? "Fun cheats: fly, god mode, spawn mobs, XP, revenge mode..."
                        : "Cheats only work in singleplayer. On servers: Info HUD, Minimap and Chunk Borders.")))
                .build();
        menuTab.active = current != Tab.MINEMENU;

        ButtonWidget boostTab = ButtonWidget.builder(
                        Text.literal("MineBoost").formatted(Formatting.GREEN, Formatting.BOLD),
                        b -> open(Tab.MINEBOOST))
                .width(TAB_WIDTH)
                .tooltip(Tooltip.of(Text.literal("FPS boost: presets, graphics settings and extra boosts.")))
                .build();
        boostTab.active = current != Tab.MINEBOOST;

        return List.of(menuTab, boostTab);
    }
}
