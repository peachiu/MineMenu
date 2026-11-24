package com.minemenu.player;

import com.minemenu.core.CheatConfig;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Remote Chest: open your ender chest, a crafting table, an anvil, an enchanting table or a chest you marked,
 * from anywhere. The screens normally close when you're more than a few blocks from the block; these don't.
 */
public final class RemoteChest {
    private RemoteChest() {
    }

    public static void enderChest(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, p) ->
                GenericContainerScreenHandler.createGeneric9x3(syncId, inventory, player.getEnderChestInventory()), Text.translatable("container.enderchest")));
    }

    public static void crafting(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, p) ->
                new CraftingScreenHandler(syncId, inventory, ScreenHandlerContext.create(player.getWorld(), player.getBlockPos())) {
                    @Override
                    public boolean canUse(PlayerEntity user) {
                        return true;
                    }
                }, Text.translatable("container.crafting")));
    }

    public static void anvil(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, p) ->
                new AnvilScreenHandler(syncId, inventory, ScreenHandlerContext.create(player.getWorld(), player.getBlockPos())) {
                    @Override
                    public boolean canUse(PlayerEntity user) {
                        return true;
                    }
                }, Text.translatable("container.repair")));
    }

    public static void enchanting(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, p) ->
                new EnchantmentScreenHandler(syncId, inventory, ScreenHandlerContext.create(player.getWorld(), player.getBlockPos())) {
                    @Override
                    public boolean canUse(PlayerEntity user) {
                        return true;
                    }
                }, Text.translatable("container.enchant")));
    }

    public static void mark(ServerPlayerEntity player) {
        HitResult hit = player.raycast(8, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && player.getWorld().getBlockEntity(blockHit.getBlockPos()) instanceof Inventory) {
            BlockPos pos = blockHit.getBlockPos();
            CheatConfig.get().setText("remote.chest", player.getServerWorld().getRegistryKey().getValue() + " " + pos.asLong());
            player.sendMessage(Text.literal("Chest marked. Open Marked Chest opens it from anywhere.").formatted(Formatting.GREEN), true);
        } else {
            player.sendMessage(Text.literal("Look at a chest or barrel (within 8 blocks)").formatted(Formatting.YELLOW), true);
        }
    }

    public static void openMarked(ServerPlayerEntity player) {
        String saved = CheatConfig.get().text("remote.chest", "");
        String[] parts = saved.split(" ");
        if (parts.length != 2 || !parts[0].equals(player.getServerWorld().getRegistryKey().getValue().toString())) {
            player.sendMessage(Text.literal("Mark a chest in this dimension first").formatted(Formatting.YELLOW), true);
            return;
        }
        BlockPos pos = BlockPos.fromLong(Long.parseLong(parts[1]));
        if (!player.getServerWorld().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            player.sendMessage(Text.literal("The marked chest's chunk isn't loaded (too far away)").formatted(Formatting.YELLOW), true);
            return;
        }
        BlockEntity blockEntity = player.getWorld().getBlockEntity(pos);
        if (!(blockEntity instanceof Inventory inventory) || inventory.size() < 27) {
            player.sendMessage(Text.literal("The marked chest is gone").formatted(Formatting.RED), true);
            return;
        }
        Inventory anywhere = new AnywhereInventory(inventory);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, p) ->
                GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, anywhere), Text.literal("Marked chest")));
    }

    /** The chest's own inventory, except that it can be used from any distance. */
    private record AnywhereInventory(Inventory inner) implements Inventory {
        @Override public int size() { return 27; }
        @Override public boolean isEmpty() { return inner.isEmpty(); }
        @Override public ItemStack getStack(int slot) { return inner.getStack(slot); }
        @Override public ItemStack removeStack(int slot, int amount) { return inner.removeStack(slot, amount); }
        @Override public ItemStack removeStack(int slot) { return inner.removeStack(slot); }
        @Override public void setStack(int slot, ItemStack stack) { inner.setStack(slot, stack); }
        @Override public void markDirty() { inner.markDirty(); }
        @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
        @Override public void clear() { inner.clear(); }
        @Override public void onOpen(PlayerEntity player) { inner.onOpen(player); }
        @Override public void onClose(PlayerEntity player) { inner.onClose(player); }
    }
}
