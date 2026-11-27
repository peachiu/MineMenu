package com.minexray.hud;

import com.minexray.EspCategory;
import com.minexray.scan.VeinMerger;
import net.minecraft.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ore Counter: how many blocks of each highlighted ore are within the radius right now. */
public final class OreCounter {
    public record Line(Block block, int color, int blocks, int veins) {
    }

    private OreCounter() {
    }

    /** Most blocks first. */
    public static List<Line> lines(List<VeinMerger.Vein> veins) {
        Map<Block, int[]> counts = new LinkedHashMap<>();
        for (VeinMerger.Vein vein : veins) {
            if (vein.category() != EspCategory.ORE) {
                continue;
            }
            int[] c = counts.computeIfAbsent(vein.block(), b -> new int[]{vein.color(), 0, 0});
            c[1] += vein.count();
            c[2]++;
        }
        List<Line> lines = new ArrayList<>();
        counts.forEach((block, c) -> lines.add(new Line(block, c[0], c[1], c[2])));
        lines.sort((a, b) -> Integer.compare(b.blocks(), a.blocks()));
        return lines;
    }
}
