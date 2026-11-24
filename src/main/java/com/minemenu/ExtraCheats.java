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
 * The MineMenu 3.0 hacks (Ore Vision moved to the client-side ESP in 3.4): Mob Glow, Vein Miner, Item Magnet, Infinite Items,
 * Keep Inventory, Always Day, Mega Knockback, and the new item and combat actions.
 * Server thread only, and only ever for the singleplayer host (see {@link ServerCheats#host}).
 */
public final class ExtraCheats {
    private static final double MAGNET_RADIUS = 12.0;
    private static final double MOB_GLOW_RADIUS = 64.0;
    private static final int MAX_VEIN_LOGS = 256;
    private static final int MAX_VEIN_ORES = 64;
    /** Game rule values from before Keep Inventory / Always Day, restored when they're turned off. */
    private static Boolean originalKeepInventory;
    private static Boolean originalWeatherCycle;

    private ExtraCheats() {
    }

    public static void onServerStarted(MinecraftServer server) {
        ItemCheats.reset();
        originalKeepInventory = null;
        originalWeatherCycle = null;
    }

    // ------------------------------------------------------------------
    // Toggles
    // ------------------------------------------------------------------

    public static void enable(MinecraftServer server, ServerPlayerEntity player, Feature feature) {
        GameRules rules = server.getGameRules();
        switch (feature) {
            case KEEP_INVENTORY -> {
                originalKeepInventory = rules.getBoolean(GameRules.KEEP_INVENTORY);
                rules.get(GameRules.KEEP_INVENTORY).set(true, server);
            }
            case ALWAYS_DAY -> {
                originalWeatherCycle = rules.getBoolean(GameRules.DO_WEATHER_CYCLE);
                com.minemenu.core.DayCycle.stop(server, com.minemenu.core.DayCycle.Reason.ALWAYS_DAY);
                rules.get(GameRules.DO_WEATHER_CYCLE).set(false, server);
                for (ServerWorld world : server.getWorlds()) {
                    world.setTimeOfDay(6000); // midday
                }
                server.getOverworld().setWeather(12000, 0, false, false);
            }
            default -> {
            }
        }
    }

    /** {@code player} may be null when the host has already left (the game rules still get restored). */
    public static void disable(MinecraftServer server, ServerPlayerEntity player, Feature feature) {
        GameRules rules = server.getGameRules();
        switch (feature) {
            case KEEP_INVENTORY -> {
                rules.get(GameRules.KEEP_INVENTORY).set(originalKeepInventory != null && originalKeepInventory, server);
                originalKeepInventory = null;
            }
            case ALWAYS_DAY -> {
                com.minemenu.core.DayCycle.release(server, com.minemenu.core.DayCycle.Reason.ALWAYS_DAY);
                rules.get(GameRules.DO_WEATHER_CYCLE).set(originalWeatherCycle == null || originalWeatherCycle, server);
                originalWeatherCycle = null;
            }
            case MOB_GLOW -> {
                if (player == null) {
                    return; // the glow wears off by itself within 2 seconds
                }
                // Remove the glow right away instead of waiting for it to wear off.
                for (LivingEntity mob : mobsAround(player, MOB_GLOW_RADIUS + 16)) {
                    StatusEffectInstance glow = mob.getStatusEffect(StatusEffects.GLOWING);
                    if (glow != null && glow.getDuration() <= 60) {
                        mob.removeStatusEffect(StatusEffects.GLOWING);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Called every server tick with the features currently applied. */
    public static void tick(ServerPlayerEntity player, Set<Feature> applied, int tickCounter) {
        if (applied.contains(Feature.ITEM_MAGNET)) {
            magnet(player);
        }
        if (applied.contains(Feature.INFINITE_ITEMS)) {
            refill(player.getMainHandStack());
            refill(player.getOffHandStack());
        }
        if (applied.contains(Feature.MOB_GLOW) && tickCounter % 10 == 0) {
            for (LivingEntity mob : mobsAround(player, MOB_GLOW_RADIUS)) {
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, false, false));
            }
        }
    }

    private static void magnet(ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(MAGNET_RADIUS);
        Vec3d target = player.getPos().add(0, 0.5, 0);
        for (ItemEntity item : player.getServerWorld().getEntitiesByClass(ItemEntity.class, box,
                item -> item.isAlive() && !item.cannotPickup() && MagnetFilter.allows(item.getStack()))) {
            item.setPosition(target);
            item.setVelocity(Vec3d.ZERO);
        }
        for (ExperienceOrbEntity orb : player.getServerWorld().getEntitiesByClass(ExperienceOrbEntity.class, box,
                Entity::isAlive)) {
            orb.setPosition(target);
        }
    }

    /** Full stack for stackable items, no wear for tools. */
    private static void refill(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (stack.isStackable() && stack.getCount() < stack.getMaxCount()) {
            stack.setCount(stack.getMaxCount());
        }
        if (stack.isDamageable() && stack.getDamage() > 0) {
            stack.setDamage(0);
        }
    }

    private static List<LivingEntity> mobsAround(ServerPlayerEntity player, double radius) {
        return player.getServerWorld().getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(radius), e -> e.isAlive() && !(e instanceof PlayerEntity));
    }

    // ------------------------------------------------------------------
    // Vein Miner (block break event) and Mega Knockback (attack event)
    // ------------------------------------------------------------------

    /** After the host breaks a block: take the rest of the tree or ore vein with it. */
    public static void afterBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state) {
        if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        MinecraftServer server = serverWorld.getServer();
        if (ServerCheats.host(server) != serverPlayer) {
            return;
        }
        List<BlockPos> brokenPositions = new ArrayList<>();
        brokenPositions.add(pos);
        if (CheatState.isOn(Feature.VEIN_MINER) && !player.isSneaking()) {
            veinMine(serverWorld, serverPlayer, pos, state, brokenPositions);
        }
        if (CheatState.isOn(Feature.AUTO_SMELT)) {
            // The drops are spawned during the break; smelt them once this tick's breaking is done.
            server.execute(() -> smeltDropsAt(serverWorld, brokenPositions));
        }
    }

    /** Also called by the GameTests. */
    public static void veinMine(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, BlockPos pos, BlockState state,
                                 List<BlockPos> brokenPositions) {
        boolean log = state.isIn(BlockTags.LOGS);
        boolean ore = OreColors.of(state) != null || isOtherOre(state);
        if (!log && !ore) {
            return;
        }
        Block block = state.getBlock();
        int limit = log ? MAX_VEIN_LOGS : MAX_VEIN_ORES;
        ItemStack tool = serverPlayer.getMainHandStack();

        // Flood fill through touching blocks of the same type (diagonals count: trees and veins zig-zag).
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(pos);
        seen.add(pos);
        int broken = 0;
        while (!queue.isEmpty() && broken < limit) {
            BlockPos current = queue.poll();
            for (BlockPos next : BlockPos.iterate(current.add(-1, -1, -1), current.add(1, 1, 1))) {
                if (broken >= limit) {
                    break;
                }
                BlockPos nextPos = next.toImmutable();
                if (!seen.add(nextPos)) {
                    continue;
                }
                BlockState nextState = serverWorld.getBlockState(nextPos);
                if (!nextState.isOf(block)) {
                    continue;
                }
                // Drop items as if mined with your tool (Fortune and Silk Touch work), plus ore XP.
                BlockEntity blockEntity = serverWorld.getBlockEntity(nextPos);
                Block.dropStacks(nextState, serverWorld, nextPos, blockEntity, serverPlayer, tool);
                nextState.onStacksDropped(serverWorld, nextPos, tool, true);
                serverWorld.breakBlock(nextPos, false, serverPlayer);
                broken++;
                brokenPositions.add(nextPos);
                queue.add(nextPos);
            }
        }
    }

    private static final String SMELTED_TAG = "minemenu_smelted";

    /** Auto Smelt: turns fresh drops at these positions into what a furnace would make of them. */
    private static void smeltDropsAt(ServerWorld world, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(0.75),
                    e -> e.isAlive() && e.age < 20 && !e.getCommandTags().contains(SMELTED_TAG))) {
                ItemStack stack = item.getStack();
                var recipe = world.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.SMELTING,
                        new net.minecraft.recipe.input.SingleStackRecipeInput(stack), world);
                if (recipe.isPresent()) {
                    ItemStack result = recipe.get().value().getResult(world.getRegistryManager()).copy();
                    result.setCount(Math.min(result.getMaxCount(), result.getCount() * stack.getCount()));
                    item.setStack(result);
                }
                item.addCommandTag(SMELTED_TAG);
            }
        }
    }

    /** Ores Ore Vision doesn't highlight but Vein Miner should still take (coal, copper, quartz...). */
    private static boolean isOtherOre(BlockState state) {
        return state.isIn(BlockTags.COAL_ORES) || state.isIn(BlockTags.COPPER_ORES)
                || state.isOf(Blocks.NETHER_QUARTZ_ORE);
    }

    /** When the host hits a mob: send it flying (applied after vanilla's own knockback). */
    public static void onAttack(PlayerEntity player, World world, Entity target) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer) || !(target instanceof LivingEntity living)) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        if (server == null || ServerCheats.host(server) != serverPlayer) {
            return;
        }
        if (CheatState.isOn(Feature.THOR_MODE)) {
            // After the normal hit lands: a (harmless-to-you) lightning bolt, 8 extra damage and fire.
            server.execute(() -> thorStrike(serverPlayer, living));
        }
        if (!CheatState.isOn(Feature.MEGA_KNOCKBACK)) {
            return;
        }
        Vec3d look = serverPlayer.getRotationVector();
        Vec3d push = new Vec3d(look.x, 0, look.z).normalize().multiply(4.0).add(0, 1.4, 0);
        // Run after this tick's attack code, which would otherwise halve the velocity.
        server.execute(() -> {
            if (target.isAlive()) {
                target.setVelocity(push);
                target.velocityModified = true;
            }
        });
    }

    private static void thorStrike(ServerPlayerEntity player, LivingEntity target) {
        if (!target.isAlive() || !(target.getWorld() instanceof ServerWorld world)) {
            return;
        }
        // Cosmetic bolt: the real one would also hit you (you're within its 3 block range) and set fires.
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(target.getPos());
            bolt.setCosmetic(true);
            world.spawnEntity(bolt);
        }
        target.timeUntilRegen = 0; // the normal hit just made it briefly immune
        target.damage(world.getDamageSources().lightningBolt(), 8.0f);
        target.setOnFireFor(4.0f);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    public static void wolfArmy(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        int spawned = 0;
        for (int i = 0; i < 5; i++) {
            net.minecraft.entity.passive.WolfEntity wolf = EntityType.WOLF.create(world);
            if (wolf == null) {
                continue;
            }
            double angle = Math.PI * 2 * i / 5;
            wolf.refreshPositionAndAngles(player.getX() + Math.cos(angle) * 2, player.getY(),
                    player.getZ() + Math.sin(angle) * 2, player.getYaw(), 0);
            wolf.setOwner(player);
            wolf.setHealth(wolf.getMaxHealth());
            world.spawnEntity(wolf);
            spawned++;
        }
        player.sendMessage(Text.literal(spawned + " wolves are on your side!").formatted(Formatting.GREEN), true);
    }

    public static void clearGroundItems(ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(64);
        int cleared = 0;
        for (Entity entity : player.getServerWorld().getOtherEntities(player, box,
                // Arrows yes; tridents no (that's your weapon, not litter).
                e -> e instanceof ItemEntity || (e instanceof PersistentProjectileEntity
                        && !(e instanceof net.minecraft.entity.projectile.TridentEntity)))) {
            entity.discard();
            cleared++;
        }
        player.sendMessage(Text.literal(cleared == 0 ? "Nothing on the ground"
                : "Cleared " + cleared + " item" + (cleared == 1 ? "" : "s") + " from the ground").formatted(Formatting.GREEN), true);
    }

    public static void fireball(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVector();
        FireballEntity fireball = new FireballEntity(player.getServerWorld(), player, look.multiply(1.5), 2);
        fireball.setPosition(player.getEyePos().add(look.multiply(2)));
        player.getServerWorld().spawnEntity(fireball);
    }

    public static void arrowRain(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        HitResult hit = player.raycast(160, 1.0f, false);
        Vec3d target = hit.getPos();
        for (int i = 0; i < 50; i++) {
            double x = target.x + (world.random.nextDouble() - 0.5) * 8;
            double z = target.z + (world.random.nextDouble() - 0.5) * 8;
            double y = target.y + 25 + world.random.nextDouble() * 10;
            ArrowEntity arrow = new ArrowEntity(world, x, y, z, new ItemStack(Items.ARROW), null);
            arrow.setOwner(player);
            arrow.setVelocity(0, -2.5, 0);
            arrow.setDamage(6.0);
            arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED; // no arrow litter
            world.spawnEntity(arrow);
        }
    }
}
