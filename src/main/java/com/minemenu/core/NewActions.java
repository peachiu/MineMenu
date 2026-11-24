package com.minemenu.core;

import com.minemenu.ServerCheats;
import com.minemenu.build.BuildTools;
import com.minemenu.build.Clipboard;
import com.minemenu.build.Selection;
import com.minemenu.build.UndoStack;
import com.minemenu.combat.CombatModules;
import com.minemenu.fun.FunEvents;
import com.minemenu.items.ItemTools;
import com.minemenu.player.RemoteChest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** The 3.4 actions (buttons), each handled by the class of its system. Server thread, host only. */
public final class NewActions {
    private NewActions() {
    }

    public static void run(MinecraftServer server, ServerPlayerEntity player, ServerCheats.Action action) {
        switch (action) {
            case OPEN_ENDER_CHEST -> RemoteChest.enderChest(player);
            case OPEN_CRAFTING -> RemoteChest.crafting(player);
            case OPEN_ANVIL -> RemoteChest.anvil(player);
            case OPEN_ENCHANTING -> RemoteChest.enchanting(player);
            case MARK_CHEST -> RemoteChest.mark(player);
            case OPEN_MARKED_CHEST -> RemoteChest.openMarked(player);
            case MOB_WAR -> CombatModules.mobWar(player);
            case DUPLICATE -> ItemTools.duplicate(player);
            case AUTO_SORT -> ItemTools.autoSort(player);
            case LOOT_REFILL -> ItemTools.lootRefill(player);
            case SET_CORNER_1 -> Selection.setFromLook(1, player);
            case SET_CORNER_2 -> Selection.setFromLook(2, player);
            case COPY -> Clipboard.copy(player);
            case PASTE_PREVIEW -> Clipboard.startPreview(player);
            case PASTE_CONFIRM -> Clipboard.paste(player);
            case ROTATE -> Clipboard.rotate(player);
            case MIRROR -> Clipboard.mirror(player);
            case FILL_SELECTION -> BuildTools.fillWithHeld(player);
            case CLEAR_SELECTION -> BuildTools.clear(player);
            case UNDO -> UndoStack.undo(player);
            case REDO -> UndoStack.redo(player);
            case METEOR_STRIKE -> FunEvents.meteorAtLook(player);
            case TORNADO -> FunEvents.tornado(player);
            default -> {
            }
        }
    }
}
