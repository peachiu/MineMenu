package com.minemenu;

import net.minecraft.util.Formatting;

/** The pages of the MineMenu screen. Every toggle and action lives on one page. */
public enum Category {
    PLAYER("Player", Formatting.AQUA),
    COMBAT("Combat", Formatting.RED),
    WORLD("World", Formatting.GREEN),
    ITEMS("Items", Formatting.GOLD);

    private final String displayName;
    private final Formatting color;

    Category(String displayName, Formatting color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public Formatting color() {
        return color;
    }
}
