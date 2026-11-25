package com.minemenu.build;

import com.minemenu.core.Guard;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Places big batches of blocks a few thousand per tick, so pasting or filling a 64x64x64 area never freezes the
 * game for more than a moment. What was there before is recorded as it goes, for Undo. Neighbour updates are
 * skipped while placing (no sand falling or water flowing mid-build).
 */
public final class BlockPlacer {
    /** About 4 ms per tick on an i3: 64x64x64 (262 144 blocks) takes about 3 seconds, the game keeps running. */
    private static final int PER_TICK = 4096;
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private static final class Job {
        final ServerWorld world;
        final List<BlockChange> changes;
        final List<BlockChange> before;
        final String name;
        final ServerPlayerEntity player;
        final boolean recordUndo;
        int next;

        Job(ServerWorld world, List<BlockChange> changes, String name, ServerPlayerEntity player, boolean recordUndo) {
            this.world = world;
            this.changes = changes;
            this.before = recordUndo ? new ArrayList<>(changes.size()) : null;
            this.name = name;
            this.player = player;
            this.recordUndo = recordUndo;
        }
    }

    private static final Deque<Job> jobs = new ArrayDeque<>();

    private BlockPlacer() {
    }

    public static boolean busy() {
        return !jobs.isEmpty();
    }

    /** Queues a batch; {@code recordUndo} false for undo/redo themselves. */
    public static void submit(ServerWorld world, List<BlockChange> changes, String name, ServerPlayerEntity player, boolean recordUndo) {
        if (changes.isEmpty()) {
            return;
        }
        jobs.add(new Job(world, changes, name, player, recordUndo));
        if (changes.size() > PER_TICK && player != null) {
            player.sendMessage(Text.literal(name + ": " + changes.size() + " blocks, about " + Math.max(1, changes.size() / PER_TICK / 20)
                    + " s...").formatted(Formatting.GRAY), true);
        }
    }

    public static void clear() {
        jobs.clear();
    }

    /** Server tick. */
    public static void tick() {
        Job job = jobs.peek();
        if (job == null) {
            return;
        }
        Guard.run("building " + job.name, () -> place(job));
    }

    private static void place(Job job) {
        int end = Math.min(job.changes.size(), job.next + PER_TICK);
        for (int i = job.next; i < end; i++) {
            BlockChange change = job.changes.get(i);
            if (!job.world.isInBuildLimit(change.pos())) {
                continue;
            }
            if (job.recordUndo) {
                BlockState old = job.world.getBlockState(change.pos());
                BlockEntity oldEntity = job.world.getBlockEntity(change.pos());
                NbtCompound oldData = oldEntity == null ? null : oldEntity.createNbtWithIdentifyingData(job.world.getRegistryManager());
                job.before.add(new BlockChange(change.pos(), old, oldData));
            }
            if (job.world.getBlockEntity(change.pos()) instanceof net.minecraft.inventory.Inventory inventory) {
                inventory.clear(); // replacing a chest: its items don't spill out
            }
            job.world.setBlockState(change.pos(), change.state(), FLAGS);
            if (change.blockEntity() != null) {
                BlockEntity blockEntity = job.world.getBlockEntity(change.pos());
                if (blockEntity != null) {
                    NbtCompound data = change.blockEntity().copy();
                    data.putInt("x", change.pos().getX());
                    data.putInt("y", change.pos().getY());
                    data.putInt("z", change.pos().getZ());
                    blockEntity.read(data, job.world.getRegistryManager());
                    blockEntity.markDirty();
                }
            }
        }
        job.next = end;
        if (job.next >= job.changes.size()) {
            jobs.poll();
            if (job.recordUndo) {
                UndoStack.push(new UndoStack.Entry(job.name, job.world, job.before, job.changes));
            }
            if (job.player != null) {
                job.player.sendMessage(Text.literal(job.name + " done (" + job.changes.size() + " blocks)").formatted(Formatting.GREEN), true);
            }
        }
    }
}
