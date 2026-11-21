package com.minemeboost;

import com.mineboost.BoostScreen;
import com.mineboost.MineBoostClient;
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
    }
}
