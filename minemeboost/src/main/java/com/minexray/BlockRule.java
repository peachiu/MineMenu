package com.minexray;

/**
 * One line of a preset's block list: a block id ("minecraft:diamond_ore") or a tag ("#minecraft:diamond_ores").
 * Deepslate variants follow the normal ore (a rule for diamond_ore also covers deepslate_diamond_ore).
 */
public final class BlockRule {
    public String id;
    /** Stays drawn in Classic X-Ray. */
    public boolean visible = true;
    /** Highlighted by Ore ESP / Block ESP. */
    public boolean esp = true;
    /** 0xRRGGBB, or -1 for the automatic colour. */
    public int color = -1;
    public String category = EspCategory.ORE.name();

    public BlockRule() {
    }

    public BlockRule(String id, int color, String category, boolean visible, boolean esp) {
        this.id = id;
        this.color = color;
        this.category = category;
        this.visible = visible;
        this.esp = esp;
    }

    public static BlockRule ore(String id, int color) {
        return new BlockRule(id, color, EspCategory.ORE.name(), true, true);
    }

    public BlockRule copy() {
        return new BlockRule(id, color, category, visible, esp);
    }

    public boolean isTag() {
        return id != null && id.startsWith("#");
    }
}
