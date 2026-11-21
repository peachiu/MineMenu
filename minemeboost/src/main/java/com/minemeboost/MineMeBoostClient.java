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
    }
}
