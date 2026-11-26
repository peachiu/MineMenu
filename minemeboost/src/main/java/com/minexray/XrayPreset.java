package com.minexray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A named block list (what Classic keeps and what the ESP modes show), plus optionally which modes and Block ESP
 * categories to switch on when you pick it. Saved as one JSON file in config/minemeboost/xray-presets/.
 */
public final class XrayPreset {
    public String name;
    public boolean builtIn;
    public List<BlockRule> blocks = new ArrayList<>();
    /** Mode id -> on/off, applied when you choose the preset yourself (not on the automatic dimension switch). */
    public Map<String, Boolean> modes = new LinkedHashMap<>();
    /** Block ESP categories switched on when you choose the preset. */
    public List<String> categories = new ArrayList<>();

    public XrayPreset() {
    }

    public XrayPreset(String name, boolean builtIn) {
        this.name = name;
        this.builtIn = builtIn;
    }

    public XrayPreset copy(String newName) {
        XrayPreset copy = new XrayPreset(newName, false);
        for (BlockRule rule : blocks) {
            copy.blocks.add(rule.copy());
        }
        copy.modes.putAll(modes);
        copy.categories.addAll(categories);
        return copy;
    }

    public BlockRule find(String id) {
        for (BlockRule rule : blocks) {
            if (id.equals(rule.id)) {
                return rule;
            }
        }
        return null;
    }

    XrayPreset add(BlockRule rule) {
        blocks.add(rule);
        return this;
    }

    XrayPreset mode(XrayMode mode, boolean on) {
        modes.put(mode.id, on);
        return this;
    }

    XrayPreset category(EspCategory category) {
        categories.add(category.name());
        return this;
    }
}
