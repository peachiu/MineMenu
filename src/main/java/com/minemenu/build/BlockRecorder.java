package com.minemenu.build;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers what was in every block a piece of vanilla code is about to change (WorldBlockRecorderMixin calls
 * {@link #beforeSet}). Used to undo vanilla structures: they decide where they go (and how high) only while
 * they're being placed, so the area can't be known beforehand. Server thread only; off except during one call.
 */
public final class BlockRecorder {
    /** Read in World.setBlockState for every block change, so a plain field (server thread only). */
    public static ServerWorld recording;
    private static final Map<BlockPos, BlockChange> before = new LinkedHashMap<>();

    private BlockRecorder() {
    }

    /** Runs {@code body} and returns what was there before each block it changed (the first time it changed it). */
    public static List<BlockChange> record(ServerWorld world, Runnable body) {
        before.clear();
        recording = world;
        try {
            body.run();
        } finally {
            recording = null;
        }
        List<BlockChange> list = new ArrayList<>(before.values());
        before.clear();
        return list;
    }

    public static void beforeSet(World world, BlockPos pos) {
        if (world != recording || before.containsKey(pos)) {
            return;
        }
        BlockPos fixed = pos.toImmutable();
        BlockEntity blockEntity = world.getBlockEntity(fixed);
        before.put(fixed, new BlockChange(fixed, world.getBlockState(fixed),
                blockEntity == null ? null : blockEntity.createNbtWithIdentifyingData(world.getRegistryManager())));
    }
}
