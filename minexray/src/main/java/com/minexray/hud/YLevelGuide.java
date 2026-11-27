package com.minexray.hud;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.List;

/**
 * Y-Level Guide: where each ore generates in 1.21 and where it's most common.
 * VERIFY: values from the 1.18+ ore distribution (unchanged through 1.21.1 as far as known); check against the
 * game's placed_feature files if a future version changes them.
 */
public final class YLevelGuide {
    /** {@code best}: the height with the most of it. Dimension: "minecraft:overworld" or "minecraft:the_nether". */
    public record Range(Block block, String dimension, int min, int max, int best) {
    }

    public static final List<Range> RANGES = List.of(
            new Range(Blocks.COAL_ORE, "minecraft:overworld", 0, 320, 96),
            new Range(Blocks.COPPER_ORE, "minecraft:overworld", -16, 112, 48),
            new Range(Blocks.IRON_ORE, "minecraft:overworld", -64, 320, 16),
            new Range(Blocks.LAPIS_ORE, "minecraft:overworld", -64, 64, 0),
            new Range(Blocks.GOLD_ORE, "minecraft:overworld", -64, 32, -16),
            new Range(Blocks.REDSTONE_ORE, "minecraft:overworld", -64, 16, -59),
            new Range(Blocks.DIAMOND_ORE, "minecraft:overworld", -64, 16, -59),
            new Range(Blocks.EMERALD_ORE, "minecraft:overworld", -16, 320, 236),
            new Range(Blocks.NETHER_QUARTZ_ORE, "minecraft:the_nether", 10, 117, 60),
            new Range(Blocks.NETHER_GOLD_ORE, "minecraft:the_nether", 10, 117, 60),
            new Range(Blocks.ANCIENT_DEBRIS, "minecraft:the_nether", 8, 119, 15));

    private YLevelGuide() {
    }

    public static boolean inRange(Range range, int y) {
        return y >= range.min() && y <= range.max();
    }
}
