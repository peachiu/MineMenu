package com.minemenu;

import net.minecraft.util.Formatting;

/** The pages of the MineMenu screen. Every toggle and action lives on one page (Build, Fun and Utility since 3.4). */
public enum Category {
    PLAYER("Player", Formatting.AQUA),
    COMBAT("Combat", Formatting.RED),
    WORLD("World", Formatting.GREEN),
    ITEMS("Items", Formatting.GOLD),
    BUILD("Build", Formatting.BLUE),
    FUN("Fun", Formatting.LIGHT_PURPLE),
    UTILITY("Utility", Formatting.GRAY);

    private final String displayName;
    private final Formatting color;

    Category(String displayName, Formatting color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return com.minemenu.core.Tr.get("minemenu.category." + name().toLowerCase(java.util.Locale.ROOT), displayName);
    }

    public Formatting color() {
        return color;
    }
}
