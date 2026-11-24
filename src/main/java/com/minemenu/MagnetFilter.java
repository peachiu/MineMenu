package com.minemenu;

import com.minemenu.core.CheatConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.HashSet;
import java.util.Set;

/**
 * Magnet Filter: Item Magnet pulls only the items on the list ("only" mode) or everything except them
 * ("except" mode). The list is item ids separated by commas, e.g. "diamond, iron_ingot, minecraft:emerald".
 */
public final class MagnetFilter {
    private static String cachedText;
    private static Set<String> cachedIds = Set.of();

    private MagnetFilter() {
    }

    public static boolean allows(ItemStack stack) {
        if (!CheatState.isOn(Feature.MAGNET_FILTER)) {
            return true;
        }
        Set<String> ids = ids();
        if (ids.isEmpty()) {
            return true;
        }
        boolean listed = ids.contains(Registries.ITEM.getId(stack.getItem()).toString());
        return exceptMode() != listed;
    }

    public static boolean exceptMode() {
        return "except".equals(CheatConfig.get().text("magnet.mode", "only"));
    }

    private static Set<String> ids() {
        String text = CheatConfig.get().text("magnet.items", "diamond, emerald, iron_ingot, gold_ingot, netherite_scrap");
        if (!text.equals(cachedText)) {
            Set<String> ids = new HashSet<>();
            for (String part : text.split("[,\\s]+")) {
                if (!part.isBlank()) {
                    ids.add(part.contains(":") ? part.trim() : "minecraft:" + part.trim());
                }
            }
            cachedIds = ids;
            cachedText = text;
        }
        return cachedIds;
    }
}
