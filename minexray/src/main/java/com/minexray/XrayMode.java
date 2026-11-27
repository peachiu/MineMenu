package com.minexray;

import org.lwjgl.glfw.GLFW;

/** MineXray's modes. Several can be on at once; each has its own key (Options > Controls > MineXray). */
public enum XrayMode {
    CLASSIC("classic", "Classic X-Ray", GLFW.GLFW_KEY_X),
    ORE_ESP("ore_esp", "Ore ESP", GLFW.GLFW_KEY_G),
    CAVE_FINDER("cave_finder", "Cave Finder", -1),
    FLUID_VIEW("fluid_view", "Fluid View", -1),
    BLOCK_ESP("block_esp", "Block ESP", -1),
    ENTITY_ESP("entity_esp", "Entity ESP", -1),
    LAVA_WARNING("lava_warning", "Lava Warning", -1);

    public final String id;
    private final String english;
    public final int defaultKey;

    XrayMode(String id, String english, int defaultKey) {
        this.id = id;
        this.english = english;
        this.defaultKey = defaultKey;
    }

    public String displayName() {
        return XrayHost.tr("minexray.mode." + id, english);
    }

    public String description() {
        return XrayHost.tr("minexray.mode." + id + ".desc", "");
    }
}
