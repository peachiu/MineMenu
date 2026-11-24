package com.minemeboost;

import com.mineboost.BoostScreen;
import com.minemenu.MineMenuScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.MinecraftClient;

/**
 * Mod Menu's "Configure" button for MineMeBoost: opens the menu (MineMenu tab in a world, MineBoost otherwise).
 * Only loaded by Mod Menu, so MineMeBoost works the same without it.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> MinecraftClient.getInstance().world != null ? new MineMenuScreen()
                : new BoostScreen(parent, MinecraftClient.getInstance().options);
    }
}
