package com.minemenu;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

import java.util.LinkedHashMap;
import java.util.Map;

/** The ores Ore Vision shows and the colour of each (Vein Miner uses the same list). Coal and copper: too common. */
public final class OreColors {
    private static final Map<TagKey<Block>, Integer> COLORS = new LinkedHashMap<>();
    public static final int ANCIENT_DEBRIS = 0xB04CFF;

    static {
        COLORS.put(BlockTags.DIAMOND_ORES, 0x55FFFF);
        COLORS.put(BlockTags.EMERALD_ORES, 0x55FF55);
        COLORS.put(BlockTags.GOLD_ORES, 0xFFD700);
        COLORS.put(BlockTags.IRON_ORES, 0xE8B88A);
        COLORS.put(BlockTags.REDSTONE_ORES, 0xFF3030);
        COLORS.put(BlockTags.LAPIS_ORES, 0x3366FF);
    }

    private OreColors() {
    }

    /** The colour, or null if Ore Vision doesn't show this block. */
    public static Integer of(BlockState state) {
        if (state.isOf(Blocks.ANCIENT_DEBRIS)) {
            return ANCIENT_DEBRIS;
        }
        if (state.isOf(Blocks.NETHER_GOLD_ORE)) {
            return COLORS.get(BlockTags.GOLD_ORES);
        }
        for (Map.Entry<TagKey<Block>, Integer> entry : COLORS.entrySet()) {
            if (state.isIn(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
