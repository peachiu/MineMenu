package com.minemenu.build;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Undo / Redo for Build tools: the last 10 changes (and at most about 4 million blocks in memory). */
public final class UndoStack {
    private static final int MAX_STEPS = 10;
    private static final int MAX_BLOCKS = 4_000_000;

    /** {@code before} restores it, {@code after} does it again. */
    public record Entry(String name, ServerWorld world, List<BlockChange> before, List<BlockChange> after) {
    }

    private static final Deque<Entry> undo = new ArrayDeque<>();
    private static final Deque<Entry> redo = new ArrayDeque<>();

    private UndoStack() {
    }

    static void push(Entry entry) {
        undo.push(entry);
        redo.clear();
        int blocks = 0;
        for (Entry e : undo) {
            blocks += e.before().size();
        }
        while (undo.size() > MAX_STEPS || (blocks > MAX_BLOCKS && undo.size() > 1)) {
            blocks -= undo.removeLast().before().size();
        }
    }

    public static void undo(ServerPlayerEntity player) {
        if (BlockPlacer.busy()) {
            player.sendMessage(Text.translatable("minemenu.text.still_building_try_again_in_a").formatted(Formatting.YELLOW), true);
            return;
        }
        Entry entry = undo.poll();
        if (entry == null) {
            player.sendMessage(Text.translatable("minemenu.text.nothing_to_undo").formatted(Formatting.YELLOW), true);
            return;
        }
        redo.push(entry);
        BlockPlacer.submit(entry.world(), entry.before(), "undo:" + entry.name(), player, false);
    }

    public static void redo(ServerPlayerEntity player) {
        if (BlockPlacer.busy()) {
            player.sendMessage(Text.translatable("minemenu.text.still_building_try_again_in_a").formatted(Formatting.YELLOW), true);
            return;
        }
        Entry entry = redo.poll();
        if (entry == null) {
            player.sendMessage(Text.translatable("minemenu.text.nothing_to_redo").formatted(Formatting.YELLOW), true);
            return;
        }
        undo.push(entry);
        BlockPlacer.submit(entry.world(), entry.after(), "redo:" + entry.name(), player, false);
    }

    public static void clear() {
        undo.clear();
        redo.clear();
    }

    /** The step Undo would take back next, or null (used by tests). */
    public static Entry last() {
        return undo.peek();
    }

    public static int size() {
        return undo.size();
    }
}
