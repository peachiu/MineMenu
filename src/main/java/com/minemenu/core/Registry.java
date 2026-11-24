package com.minemenu.core;

import com.minemenu.Category;
import com.minemenu.ui.MenuEntries;

/** Registers every 3.4 hack module, screen and key with the rest of the mod. */
public final class Registry {
    private Registry() {
    }

    public static void registerAll() {
        com.minemenu.esp.LineRenderer.init();
        KeyBinds.register();
        com.minemenu.esp.EspModules.register();
        com.minemenu.player.PlayerModules.register();
        Modules.register(com.minemenu.Feature.MORPH, new com.minemenu.player.Morph());
        com.minemenu.combat.CombatModules.register();
        com.minemenu.build.BuildModules.register();
        com.minemenu.build.BuildScreens.register();
        com.minemenu.items.ItemScreens.register();
        com.minemenu.fun.FunModules.register();
        com.minemenu.hud.HudModules.register();
        com.minemenu.hud.MacroScreen.register();
        MenuEntries.screen("boss_arena", Category.COMBAT, "Boss Arena...",
                "Summon the Wither, the Ender Dragon, the Warden or an Elder Guardian, with more health and an arena.", com.minemenu.combat.BossArena::new);
    }
}
