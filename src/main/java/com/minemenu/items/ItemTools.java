package com.minemenu.items;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Duplicate, Auto-Sort and Loot Refill. Server thread only. */
public final class ItemTools {
    private ItemTools() {
    }

    public static void duplicate(ServerPlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            player.sendMessage(Text.translatable("minemenu.text.hold_something_to_duplicate").formatted(Formatting.YELLOW), true);
            return;
        }
        ItemStack copy = held.copy();
        if (!player.getInventory().insertStack(copy)) {
            player.dropItem(copy, false);
        }
        player.sendMessage(Text.translatable("minemenu.text.duplicated_sx_s", held.getCount(), held.getName().getString()).formatted(Formatting.GREEN), true);
    }

    /** Sorts slots 9-35 (not the hotbar): stacks merged, ordered by item id. */
    public static void autoSort(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
            inventory.setStack(i, ItemStack.EMPTY);
        }
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : stacks) {
            for (ItemStack existing : merged) {
                if (stack.isEmpty()) {
                    break;
                }
                if (ItemStack.areItemsAndComponentsEqual(existing, stack) && existing.getCount() < existing.getMaxCount()) {
                    int move = Math.min(stack.getCount(), existing.getMaxCount() - existing.getCount());
                    existing.increment(move);
                    stack.decrement(move);
                }
            }
            if (!stack.isEmpty()) {
                merged.add(stack);
            }
        }
        merged.sort(Comparator.comparing((ItemStack s) -> Registries.ITEM.getId(s.getItem()).toString()).thenComparing(s -> -s.getCount()));
        for (int i = 0; i < merged.size() && i < 27; i++) {
            inventory.setStack(9 + i, merged.get(i));
        }
        player.sendMessage(Text.translatable("minemenu.text.inventory_sorted").formatted(Formatting.GREEN), true);
    }

    /** The loot table chests in this structure normally get, or null. */
    private static RegistryKey<LootTable> lootFor(String structure) {
        if (structure.startsWith("village")) return LootTables.VILLAGE_PLAINS_CHEST;
        return switch (structure) {
            case "desert_pyramid" -> LootTables.DESERT_PYRAMID_CHEST;
            case "jungle_pyramid" -> LootTables.JUNGLE_TEMPLE_CHEST;
            case "pillager_outpost" -> LootTables.PILLAGER_OUTPOST_CHEST;
            case "shipwreck", "shipwreck_beached" -> LootTables.SHIPWRECK_TREASURE_CHEST;
            case "mineshaft", "mineshaft_mesa" -> LootTables.ABANDONED_MINESHAFT_CHEST;
            case "stronghold" -> LootTables.STRONGHOLD_CORRIDOR_CHEST;
            case "fortress" -> LootTables.NETHER_BRIDGE_CHEST;
            case "bastion_remnant" -> LootTables.BASTION_OTHER_CHEST;
            case "end_city" -> LootTables.END_CITY_TREASURE_CHEST;
            case "ancient_city" -> LootTables.ANCIENT_CITY_CHEST;
            case "igloo" -> LootTables.IGLOO_CHEST_CHEST;
            case "buried_treasure" -> LootTables.BURIED_TREASURE_CHEST;
            case "mansion" -> LootTables.WOODLAND_MANSION_CHEST;
            case "trial_chambers" -> LootTables.TRIAL_CHAMBERS_SUPPLY_CHEST;
            default -> structure.startsWith("ruined_portal") ? LootTables.RUINED_PORTAL_CHEST : null;
        };
    }

    /**
     * Loot Refill: loot chests and barrels within 48 blocks that are inside a structure (village, temple, mineshaft...)
     * and were already opened get that structure's loot table back, so they fill again the next time you open them.
     * Chests you placed yourself, outside structures, are never touched.
     */
    public static void lootRefill(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos center = player.getBlockPos();
        var structures = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.STRUCTURE);
        int refilled = 0;
        ChunkPos chunk = new ChunkPos(center);
        for (int cx = chunk.x - 3; cx <= chunk.x + 3; cx++) {
            for (int cz = chunk.z - 3; cz <= chunk.z + 3; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                WorldChunk worldChunk = world.getChunk(cx, cz);
                for (BlockEntity blockEntity : worldChunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof LootableContainerBlockEntity container) || container.getLootTable() != null
                            || blockEntity.getPos().getSquaredDistance(center) > 48 * 48) {
                        continue;
                    }
                    StructureStart start = world.getStructureAccessor().getStructureContaining(blockEntity.getPos(), s -> true);
                    if (start == null || !start.hasChildren()) {
                        continue;
                    }
                    var id = structures.getId(start.getStructure());
                    RegistryKey<LootTable> loot = id == null ? null : lootFor(id.getPath());
                    if (loot != null && container.isEmpty()) {
                        container.setLootTable(loot, world.random.nextLong());
                        refilled++;
                    }
                }
            }
        }
        player.sendMessage((refilled == 0 ? Text.translatable("minemenu.text.no_empty_structure_chests_nearby_it") : Text.translatable("minemenu.text.s_chest_s_will_have_new", refilled, (refilled == 1 ? "" : "s"))).formatted(refilled == 0 ? Formatting.YELLOW : Formatting.GREEN), true);
    }
}
