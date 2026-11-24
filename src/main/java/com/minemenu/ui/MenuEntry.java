package com.minemenu.ui;

import com.minemenu.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * One thing you can do from the menu, the search box, the Command Palette, a key or a macro: a hack to switch,
 * an action to run, a screen to open, a preset, a waypoint...
 *
 * @param id       stable id for keys, favorites and macros, e.g. "feature:FLY", "action:DAY", "screen:waypoints"
 * @param isOn     for on/off hacks, whether it's on now; null for everything else
 * @param run      what happens; gets the screen it was started from (or null)
 * @param multiplayer whether it may be used on a multiplayer server
 */
public record MenuEntry(String id, String name, String description, @Nullable Category category, Kind kind,
                        @Nullable BooleanSupplier isOn, BiConsumer<MinecraftClient, Screen> run, boolean multiplayer) {
    public enum Kind { HACK, ACTION, SCREEN, PRESET, WAYPOINT, MACRO }

    public boolean matches(String query) {
        if (query.isBlank()) {
            return true;
        }
        String q = query.toLowerCase(java.util.Locale.ROOT);
        return name.toLowerCase(java.util.Locale.ROOT).contains(q) || description.toLowerCase(java.util.Locale.ROOT).contains(q)
                || (category != null && category.displayName().toLowerCase(java.util.Locale.ROOT).startsWith(q));
    }
}
