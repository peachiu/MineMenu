package com.minemenu.items;

import com.minemenu.core.WorldData;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Armor Loadouts (5 sets of armor + weapons) and Inventory Snapshots (5 whole inventories), saved per world.
 * Loading a loadout puts what you wore into your inventory first (or drops it if full).
 */
public final class Inventories {
    public static final int SLOTS = 5;
    private static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private Inventories() {
    }

    public static boolean has(ServerPlayerEntity player, String kind, int slot) {
        return WorldData.read(player.getServer()).contains(kind + slot);
    }

    public static void saveLoadout(ServerPlayerEntity player, int slot) {
        NbtCompound data = WorldData.read(player.getServer());
        NbtCompound gear = new NbtCompound();
        for (EquipmentSlot equipmentSlot : GEAR) {
            ItemStack stack = player.getEquippedStack(equipmentSlot);
            if (!stack.isEmpty()) {
                gear.put(equipmentSlot.getName(), stack.encode(player.getRegistryManager()));
            }
        }
        data.put("loadout" + slot, gear);
        WorldData.write(player.getServer(), data);
        player.sendMessage(Text.literal("Loadout " + (slot + 1) + " saved").formatted(Formatting.GREEN), true);
    }

    public static void equipLoadout(ServerPlayerEntity player, int slot) {
        NbtCompound data = WorldData.read(player.getServer());
        if (!data.contains("loadout" + slot)) {
            player.sendMessage(Text.literal("Loadout " + (slot + 1) + " is empty").formatted(Formatting.YELLOW), true);
            return;
        }
        NbtCompound gear = data.getCompound("loadout" + slot);
        for (EquipmentSlot equipmentSlot : GEAR) {
            ItemStack worn = player.getEquippedStack(equipmentSlot);
            ItemStack next = gear.contains(equipmentSlot.getName())
                    ? ItemStack.fromNbt(player.getRegistryManager(), gear.get(equipmentSlot.getName())).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
            if (next.isEmpty()) {
                continue;
            }
            player.equipStack(equipmentSlot, next);
            if (!worn.isEmpty() && !player.getInventory().insertStack(worn)) {
                player.dropItem(worn, false);
            }
        }
        player.sendMessage(Text.literal("Loadout " + (slot + 1) + " equipped").formatted(Formatting.GREEN), true);
    }

    public static void saveSnapshot(ServerPlayerEntity player, int slot) {
        NbtCompound data = WorldData.read(player.getServer());
        data.put("snapshot" + slot, player.getInventory().writeNbt(new NbtList()));
        WorldData.write(player.getServer(), data);
        player.sendMessage(Text.literal("Inventory snapshot " + (slot + 1) + " saved").formatted(Formatting.GREEN), true);
    }

    public static void restoreSnapshot(ServerPlayerEntity player, int slot) {
        NbtCompound data = WorldData.read(player.getServer());
        if (!data.contains("snapshot" + slot)) {
            player.sendMessage(Text.literal("Snapshot " + (slot + 1) + " is empty").formatted(Formatting.YELLOW), true);
            return;
        }
        player.getInventory().clear();
        player.getInventory().readNbt(data.getList("snapshot" + slot, 10));
        player.playerScreenHandler.sendContentUpdates();
        player.sendMessage(Text.literal("Inventory snapshot " + (slot + 1) + " restored").formatted(Formatting.GREEN), true);
    }
}
