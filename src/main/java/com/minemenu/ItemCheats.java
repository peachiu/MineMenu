package com.minemenu;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Item actions (split out of ExtraCheats in 3.4): Super Enchant, Give Item, Clear Junk with Undo, Delete Items
 * with Undo, Repair All and the Netherite Kit. Server thread only, host only.
 */
public final class ItemCheats {
    private ItemCheats() {
    }

    /** A new world: undo lists from the last one must not give items into this one. */
    public static void reset() {
        lastClearedJunk = List.of();
        deletedItems.clear();
    }

    public static void superEnchantHeld(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            player.sendMessage(Text.literal("Hold an item to enchant it").formatted(Formatting.YELLOW), true);
            return;
        }
        int added = superEnchant(player.getServer(), stack);
        player.sendMessage(added == 0
                ? Text.literal("No more enchantments fit on this item").formatted(Formatting.YELLOW)
                : Text.literal("Added " + added + " enchantment" + (added == 1 ? "" : "s")).formatted(Formatting.LIGHT_PURPLE), true);
    }

    /**
     * When two enchantments clash, the one earlier in this list wins (Sharpness over Smite and Bane of
     * Arthropods, Protection over Blast Protection, Fortune over Silk Touch, Mending over Infinity...).
     */
    private static final List<String> PREFERRED = List.of(
            "sharpness", "protection", "efficiency", "power", "fortune", "looting", "mending", "unbreaking",
            "depth_strider", "multishot", "loyalty", "channeling", "density");

    private static int priority(RegistryEntry<Enchantment> entry) {
        String path = entry.getKey().map(key -> key.getValue().getPath()).orElse("");
        int index = PREFERRED.indexOf(path);
        return index < 0 ? PREFERRED.size() : index;
    }

    /** Adds every compatible, non-curse enchantment at its max level. Returns how many were added. */
    private static int superEnchant(MinecraftServer server, ItemStack stack) {
        Registry<Enchantment> registry = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        List<RegistryEntry<Enchantment>> candidates = new ArrayList<>();
        registry.streamEntries().forEach(candidates::add);
        candidates.sort(Comparator.comparingInt(ItemCheats::priority)); // stable: the rest keep registry order
        int added = 0;
        for (RegistryEntry<Enchantment> entry : candidates) {
            Enchantment enchantment = entry.value();
            if (entry.isIn(EnchantmentTags.CURSE) || !enchantment.isAcceptableItem(stack)) {
                continue;
            }
            var current = EnchantmentHelper.getEnchantments(stack);
            if (current.getLevel(entry) >= enchantment.getMaxLevel()) {
                continue;
            }
            // Skip ones that clash with what's already there (e.g. Sharpness vs Smite).
            if (current.getLevel(entry) == 0 && !EnchantmentHelper.isCompatible(current.getEnchantments(), entry)) {
                continue;
            }
            stack.addEnchantment(entry, enchantment.getMaxLevel());
            added++;
        }
        return added;
    }

    /**
     * Gives {@code amount} of an item (capped at what it stacks to), either into the inventory
     * (anything that doesn't fit drops at your feet) or dropped on the block you're looking at.
     */
    public static void giveItem(ServerPlayerEntity player, Identifier itemId, int amount, boolean toGround) {
        Item item = net.minecraft.registry.Registries.ITEM.getOrEmpty(itemId).orElse(null);
        ServerWorld world = player.getServerWorld();
        if (item == null || item == Items.AIR || !item.isEnabled(world.getEnabledFeatures())) {
            return;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(amount, item.getMaxCount())));
        String what = stack.getCount() + "x " + stack.getName().getString();

        if (!toGround) {
            // insertStack takes what fits and leaves the rest in the stack it was given.
            ItemStack leftover = stack.copy();
            player.getInventory().insertStack(leftover);
            if (!leftover.isEmpty()) {
                player.dropItem(leftover, false, false);
                player.sendMessage(Text.literal("Inventory full: " + leftover.getCount() + " dropped at your feet")
                        .formatted(Formatting.YELLOW), true);
            } else {
                player.sendMessage(Text.literal("Got " + what).formatted(Formatting.GREEN), true);
            }
            return;
        }

        // Drop it on top of the block you're looking at (or a little in front of you).
        HitResult hit = player.raycast(160, 1.0f, false);
        Vec3d pos;
        if (hit instanceof net.minecraft.util.hit.BlockHitResult blockHit && hit.getType() != HitResult.Type.MISS) {
            BlockPos above = blockHit.getBlockPos().offset(blockHit.getSide());
            pos = Vec3d.ofBottomCenter(above).add(0, 0.1, 0);
        } else {
            pos = player.getEyePos().add(player.getRotationVector().multiply(2));
        }
        ItemEntity entity = new ItemEntity(world, pos.x, pos.y, pos.z, stack);
        entity.setVelocity(Vec3d.ZERO);
        entity.setPickupDelay(40); // same as items you throw yourself: you get to see it land
        world.spawnEntity(entity);
        player.sendMessage(Text.literal("Dropped " + what).formatted(Formatting.GREEN), true);
    }

    /** What Clear Junk deletes: useless mob drops and the stone you dig through while mining. */
    private static final Set<Item> JUNK = Set.of(
            Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SPIDER_EYE,
            Items.DIRT, Items.GRAVEL, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.TUFF,
            Items.COBBLED_DEEPSLATE, Items.NETHERRACK);

    /** What the last Clear Junk removed, so Undo Clear can give it back (server thread only). */
    private static List<ItemStack> lastClearedJunk = List.of();

    /** Deletes junk from the main inventory and off hand. Armor and renamed items are left alone. */
    public static void clearJunk(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        List<ItemStack> removed = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<net.minecraft.util.collection.DefaultedList<ItemStack>> lists = List.of(inventory.main, inventory.offHand);
        for (var list : lists) {
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (stack.isEmpty() || !JUNK.contains(stack.getItem())
                        || stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME)) {
                    continue;
                }
                removed.add(stack.copy());
                counts.merge(stack.getName().getString(), stack.getCount(), Integer::sum);
                list.set(i, ItemStack.EMPTY);
            }
        }
        if (removed.isEmpty()) {
            player.sendMessage(Text.literal("No junk to clear").formatted(Formatting.YELLOW), true);
            return;
        }
        lastClearedJunk = removed;
        inventory.markDirty();
        StringBuilder summary = new StringBuilder();
        counts.forEach((name, count) -> summary.append(summary.isEmpty() ? "" : ", ").append(count).append("x ").append(name));
        player.sendMessage(Text.literal("Cleared: ").formatted(Formatting.GREEN)
                .append(Text.literal(summary.toString()).formatted(Formatting.WHITE))
                .append(Text.literal("  (Undo Clear brings it back)").formatted(Formatting.GRAY)), false);
    }

    /** Gives back what the last Clear Junk removed (anything that doesn't fit drops at your feet). */
    public static void undoClearJunk(ServerPlayerEntity player) {
        if (lastClearedJunk.isEmpty()) {
            player.sendMessage(Text.literal("Nothing to undo").formatted(Formatting.YELLOW), true);
            return;
        }
        int total = 0;
        for (ItemStack stack : lastClearedJunk) {
            total += stack.getCount();
            ItemStack leftover = stack.copy();
            player.getInventory().insertStack(leftover);
            if (!leftover.isEmpty()) {
                player.dropItem(leftover, false, false);
            }
        }
        lastClearedJunk = List.of();
        player.sendMessage(Text.literal("Gave back " + total + " items").formatted(Formatting.GREEN), true);
    }

    /** Items deleted with Delete Items, newest last, with the slot they came from (server thread only). */
    private record DeletedItem(int slot, ItemStack stack) {
    }

    private static final List<DeletedItem> deletedItems = new ArrayList<>();
    private static final int MAX_UNDO = 64;

    /** Delete Items mode: deletes whatever is in that inventory slot (0-35 main, 36-39 armor, 40 off hand). */
    public static void deleteInventorySlot(ServerPlayerEntity player, int slot) {
        var inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.size()) {
            return;
        }
        ItemStack stack = inventory.getStack(slot);
        if (stack.isEmpty()) {
            return;
        }
        deletedItems.add(new DeletedItem(slot, stack.copy()));
        if (deletedItems.size() > MAX_UNDO) {
            deletedItems.remove(0);
        }
        inventory.setStack(slot, ItemStack.EMPTY);
        inventory.markDirty();
    }

    /** Gives back the last deleted item: into its old slot if that's still empty, otherwise wherever it fits. */
    public static void undoDelete(ServerPlayerEntity player) {
        if (deletedItems.isEmpty()) {
            return;
        }
        DeletedItem last = deletedItems.remove(deletedItems.size() - 1);
        var inventory = player.getInventory();
        if (inventory.getStack(last.slot()).isEmpty()) {
            inventory.setStack(last.slot(), last.stack());
        } else {
            ItemStack leftover = last.stack().copy();
            inventory.insertStack(leftover);
            if (!leftover.isEmpty()) {
                player.dropItem(leftover, false, false);
            }
        }
        inventory.markDirty();
    }

    public static void repairAll(ServerPlayerEntity player) {
        int repaired = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isDamageable() && stack.getDamage() > 0) {
                stack.setDamage(0);
                repaired++;
            }
        }
        player.sendMessage(Text.literal(repaired == 0 ? "Nothing needed repairing"
                : "Repaired " + repaired + " item" + (repaired == 1 ? "" : "s")).formatted(Formatting.GREEN), true);
    }

    public static void netheriteKit(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        Map<EquipmentSlot, Item> armor = Map.of(
                EquipmentSlot.HEAD, Items.NETHERITE_HELMET,
                EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE,
                EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS,
                EquipmentSlot.FEET, Items.NETHERITE_BOOTS);
        for (Map.Entry<EquipmentSlot, Item> piece : armor.entrySet()) {
            ItemStack stack = new ItemStack(piece.getValue());
            superEnchant(server, stack);
            // Wear it if that slot is free, otherwise put it in the inventory.
            if (player.getEquippedStack(piece.getKey()).isEmpty()) {
                player.equipStack(piece.getKey(), stack);
            } else {
                player.getInventory().offerOrDrop(stack);
            }
        }
        for (Item tool : List.of(Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE,
                Items.NETHERITE_SHOVEL, Items.BOW)) {
            ItemStack stack = new ItemStack(tool);
            superEnchant(server, stack);
            player.getInventory().offerOrDrop(stack);
        }
        player.getInventory().offerOrDrop(new ItemStack(Items.ARROW, 64));
        player.sendMessage(Text.literal("Netherite kit ready!").formatted(Formatting.LIGHT_PURPLE), true);
    }
}
