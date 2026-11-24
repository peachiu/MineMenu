package com.minemenu.fun;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.LocalHost;
import com.minemenu.core.CheatConfig;
import com.minemenu.core.CheatModule;
import com.minemenu.core.FeatureSettings;
import com.minemenu.core.Guard;
import com.minemenu.core.Modules;
import com.minemenu.core.Setting;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Chaos Mode, Lucky Blocks and the challenges (Floor is Lava, Random Drops, Mob Swap). */
public final class FunModules {
    /** Ticks until the next Chaos event; read by the HUD countdown. */
    public static volatile int chaosTicksLeft = -1;
    private static int mobSwapTicks;

    private FunModules() {
    }

    public static void register() {
        Modules.register(Feature.CHAOS_MODE, new CheatModule() {
            @Override
            public void enable(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player) {
                chaosTicksLeft = interval();
            }

            @Override
            public void disable(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player) {
                chaosTicksLeft = -1;
            }

            @Override
            public void serverTick(ServerPlayerEntity player, int tick) {
                if (chaosTicksLeft < 0 || chaosTicksLeft > interval()) {
                    chaosTicksLeft = interval();
                }
                if (--chaosTicksLeft <= 0) {
                    chaosTicksLeft = interval();
                    List<String> events = new ArrayList<>();
                    for (Setting setting : FeatureSettings.of(Feature.CHAOS_MODE)) {
                        if (setting.isToggle() && FeatureSettings.is(Feature.CHAOS_MODE, setting.key())) {
                            events.add(setting.key());
                        }
                    }
                    if (!events.isEmpty()) {
                        String event = events.get(player.getRandom().nextInt(events.size()));
                        player.sendMessage(Text.literal("CHAOS: " + label(event)).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
                        FunEvents.chaos(player, event);
                    }
                }
            }
        });
        Modules.register(Feature.LUCKY_BLOCKS, new CheatModule() { });
        Modules.register(Feature.RANDOM_DROPS, new CheatModule() { });
        Modules.register(Feature.FLOOR_IS_LAVA, new CheatModule() {
            @Override
            public void serverTick(ServerPlayerEntity player, int tick) {
                if (player.isOnGround() && !player.isSpectator() && tick % 4 == 0) {
                    BlockPos below = player.getBlockPos().down();
                    BlockState state = player.getServerWorld().getBlockState(below);
                    if (!state.isAir() && state.isFullCube(player.getWorld(), below) && !state.hasBlockEntity() && !state.isOf(Blocks.MAGMA_BLOCK)
                            && state.getHardness(player.getWorld(), below) >= 0) {
                        player.getServerWorld().setBlockState(below, Blocks.MAGMA_BLOCK.getDefaultState());
                    }
                }
            }
        });
        Modules.register(Feature.MOB_SWAP, new CheatModule() {
            @Override
            public void serverTick(ServerPlayerEntity player, int tick) {
                if (++mobSwapTicks >= 5 * 60 * 20) {
                    mobSwapTicks = 0;
                    FunEvents.swapWithMob(player);
                }
            }
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer) || !LocalHost.is(player)) {
                return true;
            }
            if (CheatState.isOn(Feature.RANDOM_DROPS) && blockEntity == null) {
                boolean[] handled = {false};
                Guard.run(Feature.RANDOM_DROPS, () -> {
                    ServerWorld serverWorld = (ServerWorld) world;
                    serverWorld.breakBlock(pos, false, player);
                    drop(serverWorld, pos, new ItemStack(randomItem(serverWorld), 1 + serverWorld.random.nextInt(3)));
                    handled[0] = true;
                });
                return !handled[0];
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClient() && CheatState.isOn(Feature.LUCKY_BLOCKS) && player instanceof ServerPlayerEntity serverPlayer
                    && LocalHost.is(player) && state.getBlock() == luckyBlock()) {
                Guard.run(Feature.LUCKY_BLOCKS, () -> lucky((ServerWorld) world, serverPlayer, pos));
            }
        });
    }

    private static int interval() {
        return (int) (FeatureSettings.get(Feature.CHAOS_MODE, "minutes") * 60 * 20);
    }

    static String label(String event) {
        for (Setting setting : FeatureSettings.of(Feature.CHAOS_MODE)) {
            if (setting.key().equals(event)) {
                return setting.label();
            }
        }
        return event;
    }

    private static Block luckyBlock() {
        String id = CheatConfig.get().text("lucky.block", "sponge");
        Identifier identifier = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        Block block = identifier == null ? Blocks.SPONGE : Registries.BLOCK.get(identifier);
        return block == Blocks.AIR ? Blocks.SPONGE : block;
    }

    private static Item randomItem(ServerWorld world) {
        List<Item> items = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item != Items.AIR && item.isEnabled(world.getEnabledFeatures())) {
                items.add(item);
            }
        }
        return items.get(world.random.nextInt(items.size()));
    }

    private static void drop(ServerWorld world, BlockPos pos, ItemStack stack) {
        world.spawnEntity(new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
    }

    /** Lucky Blocks: one surprise out of twelve, good or bad. */
    private static void lucky(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        Vec3d at = Vec3d.ofCenter(pos);
        int roll = world.random.nextInt(12);
        String what;
        switch (roll) {
            case 0 -> { drop(world, pos, new ItemStack(Items.DIAMOND, 8)); what = "8 diamonds!"; }
            case 1 -> { drop(world, pos, new ItemStack(Items.GOLDEN_APPLE, 3)); what = "golden apples"; }
            case 2 -> { drop(world, pos, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)); what = "an enchanted golden apple!"; }
            case 3 -> { drop(world, pos, new ItemStack(Items.NETHERITE_INGOT, 2)); what = "netherite!"; }
            case 4 -> { player.addExperienceLevels(10); what = "10 levels of XP"; }
            case 5 -> { EntityType.WOLF.spawn(world, pos, SpawnReason.EVENT); what = "a new friend"; }
            case 6 -> {
                for (int i = 0; i < 4; i++) EntityType.ZOMBIE.spawn(world, pos, SpawnReason.EVENT);
                what = "zombies!";
            }
            case 7 -> {
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(at);
                    world.spawnEntity(bolt);
                }
                what = "lightning!";
            }
            case 8 -> {
                world.setBlockState(pos.up(12), Blocks.ANVIL.getDefaultState());
                what = "look up!";
            }
            case 9 -> {
                for (int i = 0; i < 6; i++) EntityType.SILVERFISH.spawn(world, pos, SpawnReason.EVENT);
                what = "silverfish!";
            }
            case 10 -> { drop(world, pos, new ItemStack(Items.ELYTRA)); what = "an elytra!"; }
            default -> {
                for (int i = 0; i < 12; i++) drop(world, pos, new ItemStack(Items.COOKIE));
                what = "cookies";
            }
        }
        player.sendMessage(Text.literal("Lucky Block: " + what).formatted(roll <= 5 || roll >= 10 ? Formatting.GREEN : Formatting.RED), true);
    }
}
