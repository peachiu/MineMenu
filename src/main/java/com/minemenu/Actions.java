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

/** The one-shot actions (menu buttons). Split out of ServerCheats in 3.4. Server thread only, host only. */
public final class Actions {
    private Actions() {
    }

    public static void run(MinecraftServer server, ServerPlayerEntity player, ServerCheats.Action action) {
        ServerWorld world = player.getServerWorld();
        switch (action) {
            case KILL_HOSTILE -> report(player, killAround(player, ServerCheats.KILL_RADIUS, e -> e instanceof Monster), "hostile mob");
            case KILL_ALL -> report(player, killAround(player, ServerCheats.KILL_RADIUS, e -> true), "mob");
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
                    player.sendMessage(Text.translatable("minemenu.text.tip_turn_on_god_mode_fly")
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
            case MOB_RAIN -> MoreCheats.mobRain(player);
            case RESIZE_MOB -> MoreCheats.resizeMob(player);
            case RIDE_MOB -> MoreCheats.rideMob(player);
            case SET_HOME -> Waypoints.setHome(player);
            case GO_HOME -> Waypoints.goHome(player);
            case GROW_PLANTS -> MoreCheats.growPlants(player);
            case CLEAR_GROUND_ITEMS -> ExtraCheats.clearGroundItems(player);
            case ARROW_RAIN -> ExtraCheats.arrowRain(player);
            case SUPER_ENCHANT -> ItemCheats.superEnchantHeld(player);
            case REPAIR_ALL -> ItemCheats.repairAll(player);
            case CLEAR_JUNK -> ItemCheats.clearJunk(player);
            case UNDO_CLEAR -> ItemCheats.undoClearJunk(player);
            case NETHERITE_KIT -> ItemCheats.netheriteKit(player);
            case DAY -> world.setTimeOfDay(1000);
            case NIGHT -> world.setTimeOfDay(13000);
            case CLEAR_WEATHER -> world.setWeather(12000, 0, false, false);
            case THUNDER -> world.setWeather(0, 6000, true, true);
            default -> com.minemenu.core.NewActions.run(server, player, action);
        }
    }

    /** Spawns {@code amount} of the given mob on the block the player is looking at. */
    public static void spawnMob(ServerPlayerEntity player, Identifier typeId, int amount) {
        EntityType<?> type = Registries.ENTITY_TYPE.getOrEmpty(typeId).orElse(null);
        if (type == null || !MobList.isSpawnable(type)) {
            return;
        }
        HitResult hit = player.raycast(ServerCheats.CROSSHAIR_RANGE, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.translatable("minemenu.text.look_at_a_block_to_spawn").formatted(Formatting.YELLOW), true);
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
            player.sendMessage(Text.translatable("minemenu.text.couldn_t_spawn_s_there", name).formatted(Formatting.RED), true);
            return;
        }
        Text message = Text.translatable("minemenu.text.spawned_s", (spawned == 1 ? name : spawned + "x " + name)).formatted(Formatting.GREEN);
        if (type.getSpawnGroup() == SpawnGroup.MONSTER && world.getDifficulty() == Difficulty.PEACEFUL) {
            message = Text.translatable("minemenu.text.s_disappears_right_away_on_peaceful", name)
                    .formatted(Formatting.YELLOW);
        } else if (type.getSpawnGroup() == SpawnGroup.MONSTER && CheatState.isOn(Feature.MOB_AURA)) {
            message = Text.translatable("minemenu.text.spawned_s_but_mob_aura_is", name)
                    .formatted(Formatting.YELLOW);
        }
        player.sendMessage(message, true);
    }

    /** Sets the player's XP level to {@code levels}, or adds that many when {@code set} is false. */
    public static void giveXp(ServerPlayerEntity player, int levels, boolean set) {
        if (levels < 0) {
            return;
        }
        if (set) {
            player.setExperienceLevel(levels);
            player.setExperiencePoints(0); // start of the level, not part-way through
        } else {
            long total = (long) player.experienceLevel + levels;
            player.setExperienceLevel((int) Math.min(total, ServerCheats.MAX_XP_LEVELS));
        }
        player.sendMessage(Text.translatable("minemenu.text.you_now_have_s_levels", player.experienceLevel).formatted(Formatting.GREEN), true);
    }

    static int killAround(ServerPlayerEntity player, double radius, Predicate<LivingEntity> filter) {
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
        boolean hostile = noun.startsWith("hostile");
        Text text = count == 0 ? Text.translatable(hostile ? "minemenu.kill.none_hostile" : "minemenu.kill.none")
                : Text.translatable(hostile ? "minemenu.kill.hostile" : "minemenu.kill.all", count);
        player.sendMessage(text.copy().formatted(Formatting.RED), true);
    }

    /** The point the player is looking at, up to {@link #ServerCheats.CROSSHAIR_RANGE} blocks away. */
    private static Vec3d crosshairTarget(ServerPlayerEntity player) {
        HitResult hit = player.raycast(ServerCheats.CROSSHAIR_RANGE, 1.0f, false);
        return hit.getPos();
    }

    private static void teleportToCrosshair(ServerPlayerEntity player) {
        HitResult hit = player.raycast(ServerCheats.CROSSHAIR_RANGE, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.translatable("minemenu.text.look_at_a_block_to_teleport").formatted(Formatting.YELLOW), true);
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
