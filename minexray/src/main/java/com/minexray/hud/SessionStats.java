package com.minexray.hud;

import com.minexray.BlockFilter;
import com.minexray.EspCategory;
import com.minexray.scan.ScanCache;
import com.minexray.scan.SectionData;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.LinkedHashMap;
import java.util.Map;

/** Session Stats: ores mined since the reset (by kind), lava warnings, and how long the session has run. */
public final class SessionStats {
    private static final Map<Block, Integer> mined = new LinkedHashMap<>();
    public static int lavaWarnings;
    private static long started = System.currentTimeMillis();

    private SessionStats() {
    }

    public static void reset() {
        mined.clear();
        lavaWarnings = 0;
        started = System.currentTimeMillis();
    }

    public static Map<Block, Integer> mined() {
        return mined;
    }

    public static int total() {
        int total = 0;
        for (int count : mined.values()) {
            total += count;
        }
        return total;
    }

    public static long minutes() {
        return (System.currentTimeMillis() - started) / 60_000;
    }

    public static void tick(MinecraftClient client) {
    }

    /** A block changed on the client: if it was a highlighted ore and is now air, count it as mined. */
    public static void blockChanged(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !client.world.getBlockState(pos).isAir()) {
            return;
        }
        SectionData data = ScanCache.get(ChunkSectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        if (data == null || data.espIdx.length == 0) {
            return;
        }
        int local = ((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
        for (int i = 0; i < data.espIdx.length; i++) {
            if (data.espIdx[i] == local) {
                BlockFilter filter = BlockFilter.get();
                if (filter.category(data.espState[i]) == EspCategory.ORE) {
                    Block block = BlockFilter.groupOf(Block.getStateFromRawId(data.espState[i]).getBlock());
                    mined.merge(block, 1, Integer::sum);
                }
                return;
            }
        }
    }
}
