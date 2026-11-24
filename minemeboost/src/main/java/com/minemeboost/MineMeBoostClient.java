package com.minemeboost;

import com.mineboost.BoostConfig;
import com.mineboost.BoostScreen;
import com.mineboost.MineBoostClient;
import com.minemenu.CheatState;
import com.minemenu.MineMenuClient;
import com.minemenu.MineMenuScreen;
import net.fabricmc.api.ClientModInitializer;

/** Starts both mods, then joins their menus with the MineMenu / MineBoost tabs. */
public class MineMeBoostClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new MineMenuClient().onInitializeClient();
        new MineBoostClient().onInitializeClient();

        MineMenuClient.openMenuOverride = Hub::openLastTab;
        MineMenuScreen.tabs = screen -> Hub.tabButtons(Hub.Tab.MINEMENU);
        BoostScreen.tabs = screen -> Hub.tabButtons(Hub.Tab.MINEBOOST);

        // Only one FPS counter at a time: whichever is on greys out the other.
        MineMenuScreen.otherFpsCounterOn = () -> BoostConfig.get().fpsOverlay;
        MineMenuScreen.otherFpsCounterReason =
                "MineBoost's FPS Overlay is on. Turn it off in the MineBoost tab to use this one.";
        BoostScreen.otherFpsCounterOn = CheatState::fpsVisible;
        BoostScreen.otherFpsCounterReason =
                "MineMenu's Show FPS is on. Turn it off in the MineMenu tab to use this one.";

        // 3.4: the Command Palette and macros can also apply FPS presets and open MineBoost's tools.
        com.minemenu.ui.MenuEntries.extra = MineMeBoostClient::boostEntries;
        // Menu animations switch themselves off on the slowest presets.
        com.minemenu.ui.Theme.lowEndPreset = () -> com.mineboost.Preset.TOASTER.isActive(net.minecraft.client.MinecraftClient.getInstance())
                || com.mineboost.Preset.POTATO.isActive(net.minecraft.client.MinecraftClient.getInstance());
        // "Reset everything" in MineMenu's settings resets MineBoost's settings too (after the backup).
        com.minemenu.ui.MenuSettingsScreen.resetBoost = () -> com.mineboost.BoostConfig.replaceKeepingHistory(new com.mineboost.BoostConfig());
    }

    private static java.util.List<com.minemenu.ui.MenuEntry> boostEntries() {
        java.util.List<com.minemenu.ui.MenuEntry> list = new java.util.ArrayList<>();
        for (com.mineboost.Preset preset : com.mineboost.Preset.values()) {
            list.add(new com.minemenu.ui.MenuEntry("preset:" + preset.name(), "Preset: " + preset.displayName(), preset.description(),
                    null, com.minemenu.ui.MenuEntry.Kind.PRESET, null, (client, parent) -> preset.apply(client), true));
        }
        list.add(new com.minemenu.ui.MenuEntry("boost:lag_doctor", "Lag Doctor (30 s)", "Find out what costs you FPS.", null,
                com.minemenu.ui.MenuEntry.Kind.SCREEN, null, (client, parent) -> {
                    client.setScreen(null);
                    com.mineboost.doctor.LagDoctor.start(30);
                }, true));
        list.add(new com.minemenu.ui.MenuEntry("boost:battery", "Battery Saver (toggle)", "30 FPS, lower Render Scale and fewer particles.", null,
                com.minemenu.ui.MenuEntry.Kind.ACTION, null, (client, parent) -> {
                    com.mineboost.BoostConfig.get().batterySaver = !com.mineboost.BoostConfig.get().batterySaver;
                    com.mineboost.BoostConfig.save();
                }, true));
        list.add(new com.minemenu.ui.MenuEntry("boost:tools", "MineBoost Tools...", "Lag Doctor, A/B test, memory, context rules, FPS profiles.", null,
                com.minemenu.ui.MenuEntry.Kind.SCREEN, null, (client, parent) -> client.setScreen(new com.mineboost.tools.BoostToolsScreen(parent)), true));
        return list;
    }
}
