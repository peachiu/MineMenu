package com.minemenu.items;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.LocalHost;
import com.minemenu.Waypoints;
import com.minemenu.core.Bootstrap;
import com.minemenu.core.Guard;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;

import java.util.List;

/**
 * Death Chest: right after you die (without Keep Inventory), the items you just dropped are put into a chest
 * (two if needed) where you died, and a "Death Chest" waypoint points to it. Items that don't fit stay dropped.
 */
public final class DeathChest {
    private DeathChest() {
    }

    public static void afterDeath(LivingEntity entity) {
        if (!(entity instanceof ServerPlayerEntity player) || !CheatState.isOn(Feature.DEATH_CHEST) || !LocalHost.is(player)
                || player.getServerWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
            return;
        }
        Guard.run(Feature.DEATH_CHEST, () -> place(player));
    }

    private static void place(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        List<ItemEntity> drops = world.getEntitiesByClass(ItemEntity.class, player.getBoundingBox().expand(4), item -> item.age < 5 && item.isAlive());
        if (drops.isEmpty()) {
            return;
        }
        BlockPos pos = BlockPos.ofFloored(player.getPos());
        if (!world.isInBuildLimit(pos)) {
            return; // fell into the void: nowhere to put a chest
        }
        // Find room: up from where you died, out of lava and water.
        for (int i = 0; i < 16 && !(world.getBlockState(pos).isReplaceable() && world.getBlockState(pos.east()).isReplaceable()); i++) {
            pos = pos.up();
        }
        int chests = drops.size() > 27 ? 2 : 1;
        int placed = 0;
        for (int c = 0; c < chests; c++) {
            BlockPos chestPos = c == 0 ? pos : pos.east();
            world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
            if (!(world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
                continue;
            }
            for (int slot = 0; slot < chest.size() && placed < drops.size(); slot++) {
                ItemEntity item = drops.get(placed++);
                chest.setStack(slot, item.getStack().copy());
                item.discard();
            }
            chest.markDirty();
        }
        Bootstrap.justMadeDeathChest = true;
        BlockPos at = pos;
        Waypoints.setLastDeath(player, com.minemenu.core.Tr.get("minemenu.death_chest.waypoint", "Death Chest"));
        player.sendMessage(Text.translatable("minemenu.text.your_items_are_in_a_death", at.getX(), at.getY(), at.getZ()).formatted(Formatting.GOLD), false);
    }
}
