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
 * The MineMenu 3.0 hacks: Ore Vision, Mob Glow, Vein Miner, Item Magnet, Infinite Items,
 * Keep Inventory, Always Day, Mega Knockback, and the new item and combat actions.
 * Server thread only, and only ever for the singleplayer host (see {@link ServerCheats#host}).
 */
public final class ExtraCheats {
    private static final double MAGNET_RADIUS = 12.0;
    private static final double MOB_GLOW_RADIUS = 64.0;
    private static final int ORE_RADIUS = 20;
    private static final int MAX_ORE_MARKERS = 300;
    private static final int MAX_VEIN_LOGS = 256;
    private static final int MAX_VEIN_ORES = 64;
    private static final String ORE_MARKER_TAG = "minemenu_ore_marker";

    /** Ores Ore Vision shows, and the colour each one glows. Coal and copper are left out: far too common. */
    private static final Map<TagKey<Block>, Integer> ORE_COLORS = new LinkedHashMap<>();

    static {
        ORE_COLORS.put(BlockTags.DIAMOND_ORES, 0x55FFFF);
        ORE_COLORS.put(BlockTags.EMERALD_ORES, 0x55FF55);
        ORE_COLORS.put(BlockTags.GOLD_ORES, 0xFFD700);
        ORE_COLORS.put(BlockTags.IRON_ORES, 0xE8B88A);
        ORE_COLORS.put(BlockTags.REDSTONE_ORES, 0xFF3030);
        ORE_COLORS.put(BlockTags.LAPIS_ORES, 0x3366FF);
    }

    private static final int ANCIENT_DEBRIS_COLOR = 0xB04CFF;

    /** Ore Vision markers by block position (server thread only). */
    private static final Map<BlockPos, DisplayEntity.BlockDisplayEntity> oreMarkers = new HashMap<>();
    private static BlockPos lastOreScanCenter;

    /** Game rule values from before Keep Inventory / Always Day, restored when they're turned off. */
    private static Boolean originalKeepInventory;
    private static Boolean originalDaylightCycle;
    private static Boolean originalWeatherCycle;

    private ExtraCheats() {
    }

    public static void onServerStarted(MinecraftServer server) {
        lastClearedJunk = List.of();
        oreMarkers.clear();
        lastOreScanCenter = null;
        originalKeepInventory = null;
        originalDaylightCycle = null;
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
                originalDaylightCycle = rules.getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
                originalWeatherCycle = rules.getBoolean(GameRules.DO_WEATHER_CYCLE);
                rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                rules.get(GameRules.DO_WEATHER_CYCLE).set(false, server);
                for (ServerWorld world : server.getWorlds()) {
                    world.setTimeOfDay(6000); // midday
                }
                server.getOverworld().setWeather(12000, 0, false, false);
            }
            case ORE_VISION -> {
                lastOreScanCenter = null; // scan on the next tick
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
                rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(originalDaylightCycle == null || originalDaylightCycle, server);
                rules.get(GameRules.DO_WEATHER_CYCLE).set(originalWeatherCycle == null || originalWeatherCycle, server);
                originalDaylightCycle = null;
                originalWeatherCycle = null;
            }
            case ORE_VISION -> clearOreMarkers();
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
        if (!applied.contains(Feature.ORE_VISION) && tickCounter % 100 == 0) {
            removeStrayMarkers(player, 64); // safety net; markers aren't saved, but just in case
        }
        if (applied.contains(Feature.ORE_VISION) && tickCounter % 10 == 0) {
            BlockPos here = player.getBlockPos();
            // Rescan when you've moved a few blocks, and every 2 seconds anyway (blocks get mined).
            if (lastOreScanCenter == null || !lastOreScanCenter.isWithinDistance(here, 3) || tickCounter % 40 == 0) {
                scanOres(player);
                lastOreScanCenter = here;
            }
        }
    }

    private static void magnet(ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(MAGNET_RADIUS);
        Vec3d target = player.getPos().add(0, 0.5, 0);
        for (ItemEntity item : player.getServerWorld().getEntitiesByClass(ItemEntity.class, box,
                item -> item.isAlive() && !item.cannotPickup())) {
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
    // Ore Vision: glowing copies of the ore blocks (block display entities), visible through walls
    // ------------------------------------------------------------------

    private static void scanOres(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos center = player.getBlockPos();

        // Find ores, closest first.
        Map<BlockPos, Integer> found = new HashMap<>();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int bottom = world.getBottomY();
        int top = world.getTopY();
        for (int dx = -ORE_RADIUS; dx <= ORE_RADIUS; dx++) {
            for (int dz = -ORE_RADIUS; dz <= ORE_RADIUS; dz++) {
                for (int dy = -ORE_RADIUS; dy <= ORE_RADIUS; dy++) {
                    int y = center.getY() + dy;
                    if (y < bottom || y >= top) {
                        continue;
                    }
                    pos.set(center.getX() + dx, y, center.getZ() + dz);
                    if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                        continue;
                    }
                    Integer color = oreColor(world.getBlockState(pos));
                    if (color != null) {
                        found.put(pos.toImmutable(), color);
                    }
                }
            }
        }
        List<BlockPos> wanted = new ArrayList<>(found.keySet());
        wanted.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(center)));
        Set<BlockPos> keep = new HashSet<>(wanted.subList(0, Math.min(MAX_ORE_MARKERS, wanted.size())));

        // Remove markers for ores that were mined or are now too far.
        Iterator<Map.Entry<BlockPos, DisplayEntity.BlockDisplayEntity>> it = oreMarkers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, DisplayEntity.BlockDisplayEntity> entry = it.next();
            if (!keep.contains(entry.getKey()) || entry.getValue().isRemoved()) {
                entry.getValue().discard();
                it.remove();
            }
        }
        // Add markers for new ores.
        for (BlockPos orePos : keep) {
            if (!oreMarkers.containsKey(orePos)) {
                oreMarkers.put(orePos, spawnMarker(world, orePos, world.getBlockState(orePos), found.get(orePos)));
            }
        }
        removeStrayMarkers(player, ORE_RADIUS + 16);
    }

    /** Markers we're not tracking (left over from somewhere): remove them. */
    private static void removeStrayMarkers(ServerPlayerEntity player, double radius) {
        for (DisplayEntity.BlockDisplayEntity stray : player.getServerWorld().getEntitiesByType(EntityType.BLOCK_DISPLAY,
                player.getBoundingBox().expand(radius),
                e -> e.getCommandTags().contains(ORE_MARKER_TAG) && !oreMarkers.containsValue(e))) {
            stray.discard();
        }
    }

    private static Integer oreColor(BlockState state) {
        if (state.isOf(Blocks.ANCIENT_DEBRIS)) {
            return ANCIENT_DEBRIS_COLOR;
        }
        if (state.isOf(Blocks.NETHER_GOLD_ORE)) {
            return ORE_COLORS.get(BlockTags.GOLD_ORES);
        }
        for (Map.Entry<TagKey<Block>, Integer> entry : ORE_COLORS.entrySet()) {
            if (state.isIn(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static DisplayEntity.BlockDisplayEntity spawnMarker(ServerWorld world, BlockPos pos, BlockState state, int color) {
        // Never written to the save file: if the world autosaves while Ore Vision is on,
        // the markers mustn't come back (and keep glowing) the next time you load it.
        DisplayEntity.BlockDisplayEntity marker = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world) {
            @Override
            public boolean shouldSave() {
                return false;
            }

            @Override
            public boolean saveNbt(net.minecraft.nbt.NbtCompound nbt) {
                return false;
            }
        };
        marker.setBlockState(state);
        // A hair bigger than the real block so it doesn't flicker against it.
        marker.setTransformation(new AffineTransformation(new Vector3f(-0.005f), null, new Vector3f(1.01f), null));
        marker.setPosition(pos.getX(), pos.getY(), pos.getZ());
        marker.setGlowing(true);
        marker.setGlowColorOverride(color);
        marker.addCommandTag(ORE_MARKER_TAG);
        world.spawnEntity(marker);
        return marker;
    }

    private static void clearOreMarkers() {
        for (DisplayEntity.BlockDisplayEntity marker : oreMarkers.values()) {
            marker.discard();
        }
        oreMarkers.clear();
        lastOreScanCenter = null;
    }

    // ------------------------------------------------------------------
    // Vein Miner (block break event) and Mega Knockback (attack event)
    // ------------------------------------------------------------------

    /** After the host breaks a block: take the rest of the tree or ore vein with it. */
    public static void afterBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state) {
        if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)
                || !CheatState.isOn(Feature.VEIN_MINER) || player.isSneaking()) {
            return;
        }
        MinecraftServer server = serverWorld.getServer();
        if (ServerCheats.host(server) != serverPlayer) {
            return;
        }
        boolean log = state.isIn(BlockTags.LOGS);
        boolean ore = oreColor(state) != null || isOtherOre(state);
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
                queue.add(nextPos);
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
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)
                || !CheatState.isOn(Feature.MEGA_KNOCKBACK) || !(target instanceof LivingEntity)) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        if (server == null || ServerCheats.host(server) != serverPlayer) {
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

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

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
        candidates.sort(Comparator.comparingInt(ExtraCheats::priority)); // stable: the rest keep registry order
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
