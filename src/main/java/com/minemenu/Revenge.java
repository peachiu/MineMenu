package com.minemenu;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Revenge Mode: when a mob kills you, lightning destroys that mob, and you
 * respawn right where you died instead of at your bed or world spawn.
 */
public final class Revenge {
    /** Seconds of protection after a revenge respawn, so you don't die again straight away. */
    private static final int PROTECTION_TICKS = 5 * 20;

    private record DeathSpot(RegistryKey<World> world, Vec3d pos, float yaw, float pitch, String killerName) {
    }

    /** Where the host died to a mob, waiting for them to press Respawn. Server thread only. */
    private static DeathSpot pending;

    private Revenge() {
    }

    public static void onServerStarted(MinecraftServer server) {
        pending = null;
    }

    /** Called after any living entity dies. */
    public static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayerEntity player) || !CheatState.isOn(Feature.REVENGE)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null || ServerCheats.host(server) != player) {
            return;
        }
        LivingEntity killer = findKiller(player, source);
        if (killer == null) {
            return; // not killed by a mob (fall, lava, /kill...): respawn normally
        }

        String killerName = killer.getName().getString();
        smite(killer);

        // Dying in the void: respawning there would just kill you again.
        if (player.getY() > player.getServerWorld().getBottomY()) {
            pending = new DeathSpot(player.getServerWorld().getRegistryKey(), player.getPos(),
                    player.getYaw(), player.getPitch(), killerName);
        }
    }

    /** The mob responsible for this death, or null if it wasn't a mob. */
    private static LivingEntity findKiller(ServerPlayerEntity player, DamageSource source) {
        // getAttacker() is the shooter for arrows/fireballs and the creeper for creeper explosions.
        Entity attacker = source.getAttacker();
        LivingEntity killer = attacker instanceof LivingEntity living ? living : null;
        if (killer == null) {
            // e.g. a zombie knocked you off a cliff: Minecraft still remembers who it was.
            killer = player.getPrimeAdversary();
        }
        if (killer == null || killer == player || killer instanceof PlayerEntity) {
            return null;
        }
        return killer;
    }

    /** Strikes the mob with (harmless) lightning for the show, then kills it. */
    private static void smite(LivingEntity killer) {
        if (killer.getWorld() instanceof ServerWorld world) {
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(killer.getPos());
                bolt.setCosmetic(true); // no fire, no damage to anything else
                world.spawnEntity(bolt);
            }
        }
        // A creeper that killed you has already exploded and is gone; that's fine.
        if (killer.isAlive()) {
            killer.kill();
        }
    }

    /** Called after the player presses Respawn. */
    public static void onRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        DeathSpot spot = pending;
        pending = null;
        if (alive || spot == null) {
            return; // alive = coming back from the End, not a death
        }
        MinecraftServer server = newPlayer.getServer();
        if (server == null || ServerCheats.host(server) != newPlayer) {
            return;
        }
        ServerWorld world = server.getWorld(spot.world());
        if (world == null) {
            return;
        }
        newPlayer.teleport(world, spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), spot.pitch());
        newPlayer.fallDistance = 0;
        newPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, PROTECTION_TICKS, 4));
        newPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, PROTECTION_TICKS, 0));
        newPlayer.sendMessage(Text.literal("⚡ Revenge! The " + spot.killerName() + " that killed you has been destroyed.")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
    }
}
