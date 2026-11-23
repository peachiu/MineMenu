package com.minemenu;

import net.minecraft.block.AzaleaBlock;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.BambooShootBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.MushroomPlantBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The MineMenu 3.2 hacks: Jetpack (server side), Feather Fall, Stun Hits, Auto Replant, Peace Mode,
 * Mob Rain, Resize Mob, Ride Mob, Set/Go Home and Grow Plants. Server thread only, host only.
 */
public final class MoreCheats {
    private static final int STUN_TICKS = 200;
    private static final Identifier MOB_SIZE_ID = Identifier.of("minemenu", "mob_size");
    private static final double LOOK_RANGE = 32.0;
    private static final int GROW_RADIUS = 8;

    /** Stunned mobs and the server tick their stun ends (server thread only). */
    private static final Map<UUID, Integer> stunned = new HashMap<>();
    private static Boolean originalMobSpawning;
    private static int tickNow;

    private MoreCheats() {
    }

    public static void onServerStarted(MinecraftServer server) {
        stunned.clear();
        originalMobSpawning = null;
    }

    // ------------------------------------------------------------------
    // Toggles
    // ------------------------------------------------------------------

    public static void enable(MinecraftServer server, ServerPlayerEntity player, Feature feature) {
        switch (feature) {
            case FEATHER_FALL -> addSlowFalling(player);
            case PEACE_MODE -> {
                GameRules rules = server.getGameRules();
                originalMobSpawning = rules.getBoolean(GameRules.DO_MOB_SPAWNING);
                rules.get(GameRules.DO_MOB_SPAWNING).set(false, server);
            }
            default -> {
            }
        }
    }

    /** {@code player} may be null when the host has already left. */
    public static void disable(MinecraftServer server, ServerPlayerEntity player, Feature feature) {
        switch (feature) {
            case FEATHER_FALL -> {
                if (player != null) {
                    player.removeStatusEffect(StatusEffects.SLOW_FALLING);
                }
            }
            case PEACE_MODE -> {
                server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(originalMobSpawning == null || originalMobSpawning, server);
                originalMobSpawning = null;
            }
            case STUN_HITS -> unstunAll(server);
            default -> {
            }
        }
    }

    public static void tick(ServerPlayerEntity player, Set<Feature> applied, int tickCounter) {
        tickNow = tickCounter;
        if (applied.contains(Feature.JETPACK)) {
            player.fallDistance = 0; // landing after a jetpack flight never hurts
        }
        if (applied.contains(Feature.FEATHER_FALL) && !player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            addSlowFalling(player);
        }
        if (!stunned.isEmpty() && tickCounter % 10 == 0) {
            stunned.entrySet().removeIf(entry -> {
                if (tickCounter < entry.getValue()) {
                    return false;
                }
                unstun(player.getServer(), entry.getKey());
                return true;
            });
        }
    }

    private static void addSlowFalling(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, StatusEffectInstance.INFINITE, 0, false, false, true));
    }

    /** Stun Hits: the mob you hit stops moving (no AI) for 10 seconds. */
    public static void onAttack(PlayerEntity player, World world, Entity target) {
        if (world.isClient() || !CheatState.isOn(Feature.STUN_HITS) || !(target instanceof MobEntity mob) || !LocalHost.is(player)) {
            return;
        }
        // Remember only mobs we froze ourselves, so we never wake up one that was meant to have no AI.
        if (!mob.isAiDisabled() || stunned.containsKey(mob.getUuid())) {
            mob.setAiDisabled(true);
            mob.setVelocity(Vec3d.ZERO);
            stunned.put(mob.getUuid(), tickNow + STUN_TICKS);
        }
    }

    private static void unstun(MinecraftServer server, UUID id) {
        if (server == null) {
            return;
        }
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(id);
            if (entity instanceof MobEntity mob) {
                mob.setAiDisabled(false);
                return;
            }
        }
    }

    private static void unstunAll(MinecraftServer server) {
        for (UUID id : stunned.keySet()) {
            unstun(server, id);
        }
        stunned.clear();
    }

    /** Auto Replant: a fully grown crop you harvest goes straight back in the ground. */
    public static void afterBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state) {
        if (!(world instanceof ServerWorld serverWorld) || !CheatState.isOn(Feature.AUTO_REPLANT) || !LocalHost.is(player)) {
            return;
        }
        BlockState replant = null;
        if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
            replant = crop.withAge(0);
        } else if (state.getBlock() instanceof NetherWartBlock && state.get(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE) {
            replant = state.with(NetherWartBlock.AGE, 0);
        }
        if (replant != null) {
            BlockState seedling = replant;
            // After this tick's breaking (Vein Miner, drops) is done.
            serverWorld.getServer().execute(() -> {
                if (serverWorld.getBlockState(pos).isAir() && seedling.canPlaceAt(serverWorld, pos)) {
                    serverWorld.setBlockState(pos, seedling);
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** The living entity under your crosshair, up to {@link #LOOK_RANGE} blocks away (blocks stop the look). */
    private static LivingEntity lookedAtMob(ServerPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        double range = LOOK_RANGE;
        var blockHit = player.raycast(range, 1.0f, false);
        if (blockHit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            range = blockHit.getPos().distanceTo(eye);
        }
        Vec3d end = eye.add(look.multiply(range));
        Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, box,
                e -> e instanceof LivingEntity && !(e instanceof PlayerEntity) && e.isAlive(), range * range);
        return hit != null ? (LivingEntity) hit.getEntity() : null;
    }

    public static void rideMob(ServerPlayerEntity player) {
        LivingEntity mob = lookedAtMob(player);
        if (mob == null) {
            player.sendMessage(Text.literal("Look at a mob to ride it").formatted(Formatting.YELLOW), true);
            return;
        }
        if (player.startRiding(mob, true)) {
            player.sendMessage(Text.literal("Riding a " + mob.getName().getString() + "! Sneak to get off").formatted(Formatting.GREEN), true);
        }
    }

    public static void resizeMob(ServerPlayerEntity player) {
        LivingEntity mob = lookedAtMob(player);
        if (mob == null) {
            player.sendMessage(Text.literal("Look at a mob to resize it").formatted(Formatting.YELLOW), true);
            return;
        }
        EntityAttributeInstance scale = mob.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (scale == null) {
            return;
        }
        EntityAttributeModifier current = scale.getModifier(MOB_SIZE_ID);
        double now = current == null ? 0 : current.value();
        // normal (0) -> giant (+3 = x4) -> tiny (-0.75 = x0.25) -> normal
        double next = now == 0 ? 3.0 : now > 0 ? -0.75 : 0;
        scale.removeModifier(MOB_SIZE_ID);
        if (next != 0) {
            // Persistent: the mob stays that size, even after you reload the world.
            scale.addPersistentModifier(new EntityAttributeModifier(MOB_SIZE_ID, next, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        String size = next > 0 ? "GIANT" : next < 0 ? "tiny" : "normal size";
        player.sendMessage(Text.literal(mob.getName().getString() + " is now " + size).formatted(Formatting.LIGHT_PURPLE), true);
    }

    private static final Set<EntityType<?>> NO_RAIN = Set.of(EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.WARDEN,
            EntityType.ELDER_GUARDIAN, EntityType.GIANT, EntityType.GHAST);

    public static void mobRain(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        List<EntityType<?>> mobs = MobList.all().stream().filter(type -> !NO_RAIN.contains(type)).toList();
        int spawned = 0;
        for (int i = 0; i < 20; i++) {
            EntityType<?> type = mobs.get(world.random.nextInt(mobs.size()));
            double x = player.getX() + (world.random.nextDouble() - 0.5) * 16;
            double z = player.getZ() + (world.random.nextDouble() - 0.5) * 16;
            double y = player.getY() + 20 + world.random.nextDouble() * 10;
            Entity mob = type.spawn(world, BlockPos.ofFloored(x, y, z), SpawnReason.COMMAND);
            if (mob != null) {
                mob.refreshPositionAndAngles(x, y, z, world.random.nextFloat() * 360, 0);
                spawned++;
            }
        }
        player.sendMessage(Text.literal("It's raining " + spawned + " mobs!").formatted(Formatting.GOLD), true);
    }

    /** Grow Plants: every crop, sapling, stem, bush, bamboo and mushroom within 8 blocks grows fully. */
    public static void growPlants(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos center = player.getBlockPos();
        int grown = 0;
        for (BlockPos pos : BlockPos.iterate(center.add(-GROW_RADIUS, -3, -GROW_RADIUS), center.add(GROW_RADIUS, 3, GROW_RADIUS))) {
            BlockPos at = pos.toImmutable();
            BlockState state = world.getBlockState(at);
            if (state.getBlock() instanceof NetherWartBlock && state.get(NetherWartBlock.AGE) < NetherWartBlock.MAX_AGE) {
                world.setBlockState(at, state.with(NetherWartBlock.AGE, NetherWartBlock.MAX_AGE));
                grown++;
                continue;
            }
            if (!isGrowable(state)) {
                continue;
            }
            boolean any = false;
            // Like using bone meal over and over until it can't grow any more (saplings become trees).
            for (int i = 0; i < 16; i++) {
                BlockState now = world.getBlockState(at);
                if (!(now.getBlock() instanceof Fertilizable fertilizable) || !isGrowable(now)
                        || !fertilizable.isFertilizable(world, at, now)) {
                    break;
                }
                fertilizable.grow(world, world.random, at, now);
                any = true;
            }
            if (any) {
                grown++;
            }
        }
        player.sendMessage(Text.literal(grown == 0 ? "Nothing to grow nearby" : "Grew " + grown + " plant" + (grown == 1 ? "" : "s"))
                .formatted(Formatting.GREEN), true);
    }

    /** Plants only: not grass blocks, moss and nylium, which would cover the area in weeds. */
    private static boolean isGrowable(BlockState state) {
        var block = state.getBlock();
        return block instanceof CropBlock || block instanceof SaplingBlock || block instanceof StemBlock
                || block instanceof SweetBerryBushBlock || block instanceof CocoaBlock || block instanceof AzaleaBlock
                || block instanceof MushroomPlantBlock || block instanceof BambooBlock || block instanceof BambooShootBlock;
    }

    // ------------------------------------------------------------------
    // Home: saved in the world folder, so each world has its own
    // ------------------------------------------------------------------

    private static Path homeFile(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("minemenu_home.txt");
    }

    public static void setHome(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        String line = player.getServerWorld().getRegistryKey().getValue() + " " + player.getX() + " " + player.getY() + " "
                + player.getZ() + " " + player.getYaw() + " " + player.getPitch();
        try {
            Files.writeString(homeFile(server), line);
            player.sendMessage(Text.literal("Home set here").formatted(Formatting.GREEN), true);
        } catch (Exception e) {
            player.sendMessage(Text.literal("Couldn't save your home").formatted(Formatting.RED), true);
        }
    }

    public static void goHome(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        try {
            Path file = homeFile(server);
            if (!Files.exists(file)) {
                player.sendMessage(Text.literal("No home yet: use Set Home first").formatted(Formatting.YELLOW), true);
                return;
            }
            String[] parts = Files.readString(file).trim().split(" ");
            RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(parts[0]));
            ServerWorld world = server.getWorld(dimension);
            if (world == null) {
                return;
            }
            player.teleport(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
            player.fallDistance = 0;
            player.sendMessage(Text.literal("Welcome home!").formatted(Formatting.GREEN), true);
        } catch (Exception e) {
            player.sendMessage(Text.literal("Couldn't read your home").formatted(Formatting.RED), true);
        }
    }
}
