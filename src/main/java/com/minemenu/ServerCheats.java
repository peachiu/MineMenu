package com.minemenu;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Everything that actually changes the game. All of this runs on the
 * integrated server thread, and only ever touches the singleplayer host.
 */
public final class ServerCheats {
    private static final Identifier SPEED_ID = id("speed");
    private static final Identifier DAMAGE_ID = id("one_hit_kill");
    private static final Identifier JUMP_ID = id("super_jump");
    private static final Identifier FALL_ID = id("no_fall_damage");
    private static final Identifier NO_FALL_ID = id("no_fall_damage_toggle");
    private static final Identifier MINE_ID = id("insta_mine");
    private static final Identifier BLOCK_REACH_ID = id("block_reach");
    private static final Identifier ENTITY_REACH_ID = id("entity_reach");
    private static final Identifier SCALE_ID = id("size");

    private static final float DEFAULT_FLY_SPEED = 0.05f;
    private static final float FAST_FLY_SPEED = 0.15f;
    private static final double MOB_AURA_RADIUS = 8.0;
    private static final double KILL_RADIUS = 64.0;
    private static final double CROSSHAIR_RANGE = 160.0;
    /** High enough for anything fun; the XP bar stops working properly far above this. */
    public static final int MAX_XP_LEVELS = 100_000;

    /** Features whose effect is currently applied to the player. */
    private static final Set<Feature> applied = EnumSet.noneOf(Feature.class);
    private static CheatState.Size appliedSize = CheatState.Size.NORMAL;
    private static int tickCounter;

    private ServerCheats() {
    }

    private static Identifier id(String path) {
        return Identifier.of("minemenu", path);
    }

    // ------------------------------------------------------------------
    // Singleplayer guard
    // ------------------------------------------------------------------

    /** True only for a local singleplayer world that has not been opened to LAN. */
    public static boolean isPrivateSingleplayer(MinecraftServer server) {
        return server instanceof IntegratedServer && !server.isRemote();
    }

    /** The singleplayer host, or null if this isn't a private singleplayer world. */
    public static ServerPlayerEntity host(MinecraftServer server) {
        if (!isPrivateSingleplayer(server)) {
            return null;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (server.isHost(player.getGameProfile())) {
                return player;
            }
        }
        return null;
    }

    public static boolean isProtectedHost(LivingEntity entity) {
        return entity instanceof ServerPlayerEntity player
                && CheatState.isOn(Feature.GOD_MODE)
                && player.getServer() != null
                && host(player.getServer()) == player;
    }

    // ------------------------------------------------------------------
    // Per-tick toggles
    // ------------------------------------------------------------------

    public static void onServerStarted(MinecraftServer server) {
        // A fresh world: nothing from the previous world is applied here yet.
        applied.clear();
        appliedSize = CheatState.Size.NORMAL;
    }

    public static void tick(MinecraftServer server) {
        ServerPlayerEntity player = host(server);
        if (player == null) {
            // Opened to LAN (or no host yet): switch everything off.
            if (server instanceof IntegratedServer && server.isRemote()) {
                revertAll(server);
            }
            return;
        }
        tickCounter++;

        // Feature toggles: apply on enable, undo on disable.
        for (Feature feature : Feature.values()) {
            boolean want = CheatState.isOn(feature);
            boolean has = applied.contains(feature);
            if (want && !has) {
                enable(server, player, feature);
                applied.add(feature);
            } else if (!want && has) {
                disable(server, player, feature);
                applied.remove(feature);
            }
        }

        CheatState.Size size = CheatState.size();
        if (size != appliedSize) {
            setModifier(player, EntityAttributes.GENERIC_SCALE, SCALE_ID, size.scaleModifier(),
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            appliedSize = size;
        }

        // Things that need refreshing every tick.
        if (applied.contains(Feature.FLY) && !player.getAbilities().allowFlying) {
            // e.g. after switching game mode or respawning
            player.getAbilities().allowFlying = true;
            player.sendAbilitiesUpdate();
        }
        if (applied.contains(Feature.GOD_MODE)) {
            keepAlive(player);
        }
        if (applied.contains(Feature.NIGHT_VISION) && !player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
            addNightVision(player);
        }
        if (applied.contains(Feature.SUPER_JUMP)) {
            player.fallDistance = 0;
        }
        if (applied.contains(Feature.MOB_AURA) && tickCounter % 4 == 0) {
            killAround(player, MOB_AURA_RADIUS, e -> e instanceof Monster);
        }
        // Attribute modifiers are lost on respawn, so re-check them periodically.
        if (tickCounter % 20 == 0) {
            reapplyAttributes(player);
        }
        ExtraCheats.tick(player, applied, tickCounter);
    }

    private static void enable(MinecraftServer server, ServerPlayerEntity player, Feature feature) {
        switch (feature) {
            case FLY -> {
                player.getAbilities().allowFlying = true;
                player.sendAbilitiesUpdate();
            }
            case GOD_MODE -> keepAlive(player);
            case NIGHT_VISION -> addNightVision(player);
            case FREEZE_WORLD -> server.getTickManager().setFrozen(true);
            case SPEED -> {
                player.getAbilities().setFlySpeed(FAST_FLY_SPEED);
                player.sendAbilitiesUpdate();
                applyAttributes(player, feature);
            }
            case MOB_AURA, REVENGE, WATER_WALK, SPIDER_CLIMB, THOR_MODE, EXPLOSIVE_ARROWS, AUTO_SMELT -> {
                // Mob Aura runs in tick(); the rest react to events and mixins (Revenge, ExtraCheats, mixin package)
            }
            case ITEM_MAGNET, INFINITE_ITEMS, KEEP_INVENTORY, ALWAYS_DAY, ORE_VISION, MOB_GLOW, VEIN_MINER, MEGA_KNOCKBACK -> ExtraCheats.enable(server, player, feature);
            default -> applyAttributes(player, feature);
        }
    }

    private static void disable(MinecraftServer server, ServerPlayerEntity player, Feature feature) {
        switch (feature) {
            case FLY -> {
                PlayerAbilities abilities = player.getAbilities();
                if (!player.interactionManager.getGameMode().isCreative() && !player.isSpectator()) {
                    abilities.allowFlying = false;
                    abilities.flying = false;
                }
                player.sendAbilitiesUpdate();
            }
            case NIGHT_VISION -> player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            case FREEZE_WORLD -> server.getTickManager().setFrozen(false);
            case SPEED -> {
                player.getAbilities().setFlySpeed(DEFAULT_FLY_SPEED);
                player.sendAbilitiesUpdate();
                removeAttributes(player, feature);
            }
            case GOD_MODE, MOB_AURA, REVENGE, WATER_WALK, SPIDER_CLIMB, THOR_MODE, EXPLOSIVE_ARROWS, AUTO_SMELT -> {
                // nothing to undo
            }
            case ITEM_MAGNET, INFINITE_ITEMS, KEEP_INVENTORY, ALWAYS_DAY, ORE_VISION, MOB_GLOW, VEIN_MINER, MEGA_KNOCKBACK -> ExtraCheats.disable(server, player, feature);
            default -> removeAttributes(player, feature);
        }
    }

    private static void applyAttributes(ServerPlayerEntity player, Feature feature) {
        switch (feature) {
            case SPEED -> setModifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID, 2.0,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            case ONE_HIT_KILL -> setModifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE_ID, 1000.0,
                    EntityAttributeModifier.Operation.ADD_VALUE);
            case SUPER_JUMP -> {
                setModifier(player, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP_ID, 1.1,
                        EntityAttributeModifier.Operation.ADD_VALUE);
                setModifier(player, EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, FALL_ID, -1.0,
                        EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
            case INSTA_MINE -> setModifier(player, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, MINE_ID, 299.0,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            case NO_FALL_DAMAGE -> setModifier(player, EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, NO_FALL_ID, -1.0,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            case LONG_REACH -> {
                setModifier(player, EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, 15.5,
                        EntityAttributeModifier.Operation.ADD_VALUE);
                setModifier(player, EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID, 17.0,
                        EntityAttributeModifier.Operation.ADD_VALUE);
            }
            default -> {
            }
        }
    }

    private static void removeAttributes(ServerPlayerEntity player, Feature feature) {
        switch (feature) {
            case SPEED -> removeModifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ID);
            case ONE_HIT_KILL -> removeModifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE_ID);
            case SUPER_JUMP -> {
                removeModifier(player, EntityAttributes.GENERIC_JUMP_STRENGTH, JUMP_ID);
                removeModifier(player, EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, FALL_ID);
            }
            case INSTA_MINE -> removeModifier(player, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, MINE_ID);
            case NO_FALL_DAMAGE -> removeModifier(player, EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, NO_FALL_ID);
            case LONG_REACH -> {
                removeModifier(player, EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID);
                removeModifier(player, EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID);
            }
            default -> {
            }
        }
    }

    private static void reapplyAttributes(ServerPlayerEntity player) {
        if (applied.contains(Feature.SPEED) && player.getAbilities().getFlySpeed() != FAST_FLY_SPEED) {
            player.getAbilities().setFlySpeed(FAST_FLY_SPEED);
            player.sendAbilitiesUpdate();
        }
        for (Feature feature : applied) {
            applyAttributes(player, feature);
        }
        if (appliedSize != CheatState.Size.NORMAL) {
            setModifier(player, EntityAttributes.GENERIC_SCALE, SCALE_ID, appliedSize.scaleModifier(),
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
    }

    /** Undo everything, e.g. before the world saves, so nothing sticks to the save file. */
    public static void revertAll(MinecraftServer server) {
        if (!(server instanceof IntegratedServer)) {
            return;
        }
        ServerPlayerEntity player = null;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (server.isHost(p.getGameProfile())) {
                player = p;
            }
        }
        for (Feature feature : List.copyOf(applied)) {
            if (player != null) {
                disable(server, player, feature);
            } else if (feature == Feature.FREEZE_WORLD) {
                server.getTickManager().setFrozen(false);
            } else {
                ExtraCheats.disable(server, null, feature); // restores game rules, removes Ore Vision markers
            }
        }
        applied.clear();
        if (player != null && appliedSize != CheatState.Size.NORMAL) {
            removeModifier(player, EntityAttributes.GENERIC_SCALE, SCALE_ID);
        }
        appliedSize = CheatState.Size.NORMAL;
    }

    private static void setModifier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute,
                                    Identifier id, double value, EntityAttributeModifier.Operation operation) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) {
            return;
        }
        EntityAttributeModifier existing = instance.getModifier(id);
        if (existing != null && existing.value() == value && existing.operation() == operation) {
            return;
        }
        instance.removeModifier(id);
        if (value != 0.0) {
            instance.addTemporaryModifier(new EntityAttributeModifier(id, value, operation));
        }
    }

    private static void removeModifier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute,
                                       Identifier id) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private static void keepAlive(ServerPlayerEntity player) {
        if (player.isDead()) {
            return;
        }
        if (player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0f);
        player.setAir(player.getMaxAir());
        player.extinguish();
        player.setFrozenTicks(0);
    }

    private static void addNightVision(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION,
                StatusEffectInstance.INFINITE, 0, false, false, true));
    }

    // ------------------------------------------------------------------
    // One-shot actions (buttons in the menu)
    // ------------------------------------------------------------------

    public enum Action {
        // Player
        TELEPORT(Category.PLAYER, "Teleport", "Teleport to the block you're looking at."),
        LAUNCH(Category.PLAYER, "Launch Me", "Shoot yourself into the sky."),
        HEAL(Category.PLAYER, "Heal & Feed", "Restore full health and hunger."),
        // Combat
        KILL_HOSTILE(Category.COMBAT, "Kill Hostile Mobs", "Kill every hostile mob within 64 blocks."),
        KILL_ALL(Category.COMBAT, "Kill ALL Mobs", "Kill every mob within 64 blocks (not you, pets or armor stands)."),
        SMITE(Category.COMBAT, "Smite", "Strike lightning where you're looking."),
        FIREBALL(Category.COMBAT, "Fireball", "Shoot an exploding fireball where you're looking."),
        ARROW_RAIN(Category.COMBAT, "Arrow Rain", "50 arrows fall from the sky where you're looking."),
        WOLF_ARMY(Category.COMBAT, "Wolf Army", "5 tamed wolves appear around you and fight for you."),
        // World
        EXPLODE(Category.WORLD, "Explode", "Cause an explosion where you're looking. Turn on God Mode first!"),
        TNT_RAIN(Category.WORLD, "TNT Rain", "Drop a ring of lit TNT around you. Turn on God Mode first!"),
        DAY(Category.WORLD, "Set Day", "Set the time to morning."),
        NIGHT(Category.WORLD, "Set Night", "Set the time to night."),
        CLEAR_WEATHER(Category.WORLD, "Clear Weather", "Stop rain and thunder."),
        THUNDER(Category.WORLD, "Thunderstorm", "Start a thunderstorm."),
        CLEAR_GROUND_ITEMS(Category.WORLD, "Clear Ground Items", "Remove every dropped item and stuck arrow within 64 blocks (handy after explosions)."),
        // Items
        SUPER_ENCHANT(Category.ITEMS, "Super Enchant", "Put every enchantment that fits on the item in your hand, at max level."),
        REPAIR_ALL(Category.ITEMS, "Repair All", "Fully repair every item in your inventory."),
        CLEAR_JUNK(Category.ITEMS, "Clear Junk", "Delete junk from your inventory: rotten flesh, poisonous potatoes, spider eyes, "
                + "dirt, gravel, granite, diorite, andesite, tuff, cobbled deepslate and netherrack. "
                + "Armor and items you renamed are never touched. Undo Clear brings it all back."),
        UNDO_CLEAR(Category.ITEMS, "Undo Clear", "Get back everything the last Clear Junk removed."),
        NETHERITE_KIT(Category.ITEMS, "Netherite Kit", "Get a full set of super-enchanted netherite armor and tools.");

        private final Category category;
        private final String displayName;
        private final String description;

        Action(Category category, String displayName, String description) {
            this.category = category;
            this.displayName = displayName;
            this.description = description;
        }

        public Category category() {
            return category;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }
    }

    public static void run(MinecraftServer server, Action action) {
        ServerPlayerEntity player = host(server);
        if (player == null) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        switch (action) {
            case KILL_HOSTILE -> report(player, killAround(player, KILL_RADIUS, e -> e instanceof Monster), "hostile mob");
            case KILL_ALL -> report(player, killAround(player, KILL_RADIUS, e -> true), "mob");
            case SMITE -> {
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(crosshairTarget(player));
                    bolt.setChanneler(player);
                    world.spawnEntity(bolt);
                }
            }
            case EXPLODE -> {
                Vec3d pos = crosshairTarget(player);
                world.createExplosion(player, pos.x, pos.y, pos.z, 4.0f, World.ExplosionSourceType.TNT);
            }
            case TNT_RAIN -> {
                int count = 12;
                for (int i = 0; i < count; i++) {
                    double angle = (Math.PI * 2 * i) / count;
                    double radius = 4 + world.random.nextDouble() * 6;
                    TntEntity tnt = new TntEntity(world,
                            player.getX() + Math.cos(angle) * radius,
                            player.getY() + 12 + world.random.nextDouble() * 6,
                            player.getZ() + Math.sin(angle) * radius,
                            player);
                    tnt.setFuse(60 + world.random.nextInt(30));
                    world.spawnEntity(tnt);
                }
            }
            case TELEPORT -> teleportToCrosshair(player);
            case LAUNCH -> {
                player.setVelocity(player.getVelocity().x, 3.0, player.getVelocity().z);
                player.velocityModified = true;
                if (!CheatState.isOn(Feature.GOD_MODE) && !CheatState.isOn(Feature.SUPER_JUMP)) {
                    player.sendMessage(Text.literal("Tip: turn on God Mode, Fly or Super Jump before you land!")
                            .formatted(Formatting.YELLOW), true);
                }
            }
            case HEAL -> {
                player.setHealth(player.getMaxHealth());
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(20.0f);
                player.extinguish();
                player.setAir(player.getMaxAir());
            }
            case FIREBALL -> ExtraCheats.fireball(player);
            case WOLF_ARMY -> ExtraCheats.wolfArmy(player);
            case CLEAR_GROUND_ITEMS -> ExtraCheats.clearGroundItems(player);
            case ARROW_RAIN -> ExtraCheats.arrowRain(player);
            case SUPER_ENCHANT -> ExtraCheats.superEnchantHeld(player);
            case REPAIR_ALL -> ExtraCheats.repairAll(player);
            case CLEAR_JUNK -> ExtraCheats.clearJunk(player);
            case UNDO_CLEAR -> ExtraCheats.undoClearJunk(player);
            case NETHERITE_KIT -> ExtraCheats.netheriteKit(player);
            case DAY -> world.setTimeOfDay(1000);
            case NIGHT -> world.setTimeOfDay(13000);
            case CLEAR_WEATHER -> world.setWeather(12000, 0, false, false);
            case THUNDER -> world.setWeather(0, 6000, true, true);
        }
    }

    /** Spawns {@code amount} of the given mob on the block the player is looking at. */
    public static void spawnMob(MinecraftServer server, Identifier typeId, int amount) {
        ServerPlayerEntity player = host(server);
        if (player == null) {
            return;
        }
        EntityType<?> type = Registries.ENTITY_TYPE.getOrEmpty(typeId).orElse(null);
        if (type == null || !MobList.isSpawnable(type)) {
            return;
        }
        HitResult hit = player.raycast(CROSSHAIR_RANGE, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.literal("Look at a block to spawn a mob there").formatted(Formatting.YELLOW), true);
            return;
        }
        ServerWorld world = player.getServerWorld();
        BlockPos pos = blockHit.getBlockPos().offset(blockHit.getSide());

        int spawned = 0;
        for (int i = 0; i < amount; i++) {
            Entity entity = type.spawn(world, pos, SpawnReason.COMMAND);
            if (entity == null) {
                continue;
            }
            spawned++;
            if (i > 0) {
                // Spread a group out a little so they don't all stack in one spot.
                entity.refreshPositionAndAngles(
                        entity.getX() + (world.random.nextDouble() - 0.5) * 3,
                        entity.getY(),
                        entity.getZ() + (world.random.nextDouble() - 0.5) * 3,
                        entity.getYaw(), entity.getPitch());
            }
            // Face the player.
            float yaw = player.getYaw() + 180.0f;
            entity.setYaw(yaw);
            entity.setHeadYaw(yaw);
            entity.setBodyYaw(yaw);
        }

        String name = type.getName().getString();
        if (spawned == 0) {
            player.sendMessage(Text.literal("Couldn't spawn " + name + " there").formatted(Formatting.RED), true);
            return;
        }
        Text message = Text.literal("Spawned " + (spawned == 1 ? name : spawned + "x " + name)).formatted(Formatting.GREEN);
        if (type.getSpawnGroup() == SpawnGroup.MONSTER && world.getDifficulty() == Difficulty.PEACEFUL) {
            message = Text.literal(name + " disappears right away on Peaceful. Change the difficulty to see it.")
                    .formatted(Formatting.YELLOW);
        } else if (type.getSpawnGroup() == SpawnGroup.MONSTER && CheatState.isOn(Feature.MOB_AURA)) {
            message = Text.literal("Spawned " + name + ", but Mob Aura is on, so it won't last long!")
                    .formatted(Formatting.YELLOW);
        }
        player.sendMessage(message, true);
    }

    /** Sets the player's XP level to {@code levels}, or adds that many when {@code set} is false. */
    public static void giveXp(MinecraftServer server, int levels, boolean set) {
        ServerPlayerEntity player = host(server);
        if (player == null || levels < 0) {
            return;
        }
        if (set) {
            player.setExperienceLevel(levels);
            player.setExperiencePoints(0); // start of the level, not part-way through
        } else {
            long total = (long) player.experienceLevel + levels;
            player.setExperienceLevel((int) Math.min(total, MAX_XP_LEVELS));
        }
        player.sendMessage(Text.literal("You now have " + player.experienceLevel + " levels").formatted(Formatting.GREEN), true);
    }

    private static int killAround(ServerPlayerEntity player, double radius, Predicate<LivingEntity> filter) {
        List<LivingEntity> targets = player.getServerWorld().getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(radius),
                e -> e.isAlive()
                        && !(e instanceof PlayerEntity)
                        && !(e instanceof ArmorStandEntity)
                        && !(e instanceof TameableEntity tame && tame.isTamed())
                        && filter.test(e));
        for (LivingEntity target : targets) {
            target.damage(player.getDamageSources().playerAttack(player), Float.MAX_VALUE);
            if (target.isAlive()) {
                target.kill();
            }
        }
        return targets.size();
    }

    private static void report(ServerPlayerEntity player, int count, String noun) {
        String text = count == 0 ? "No " + noun + "s nearby" : "Killed " + count + " " + noun + (count == 1 ? "" : "s");
        player.sendMessage(Text.literal(text).formatted(Formatting.RED), true);
    }

    /** The point the player is looking at, up to {@link #CROSSHAIR_RANGE} blocks away. */
    private static Vec3d crosshairTarget(ServerPlayerEntity player) {
        HitResult hit = player.raycast(CROSSHAIR_RANGE, 1.0f, false);
        return hit.getPos();
    }

    private static void teleportToCrosshair(ServerPlayerEntity player) {
        HitResult hit = player.raycast(CROSSHAIR_RANGE, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.literal("Look at a block to teleport to it").formatted(Formatting.YELLOW), true);
            return;
        }
        BlockPos target = blockHit.getBlockPos().offset(blockHit.getSide());
        // Step up until there is room for the player to stand.
        ServerWorld world = player.getServerWorld();
        for (int i = 0; i < 8 && !(world.isAir(target) && world.isAir(target.up())); i++) {
            target = target.up();
        }
        player.fallDistance = 0;
        player.networkHandler.requestTeleport(target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                player.getYaw(), player.getPitch());
    }
}
