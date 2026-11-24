package com.minemenu.core;

import com.minemenu.LocalHost;
import com.minemenu.ServerCheats;
import com.minemenu.SurvivalCheats;
import com.minemenu.Waypoints;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Starts the MineMenu 3.4 systems: saved state, Safe Mode, the hack modules and their events. Kept apart
 * from MineMenuClient (which keeps the 3.3 set-up) so neither class grows past a readable size.
 */
public final class Bootstrap {
    private static int ticks;
    /** Things to clear when you leave a world (caches of ESP, map, health bars...). */
    private static final List<Runnable> onLeaveWorld = new ArrayList<>();

    private Bootstrap() {
    }

    public static void init() {
        boolean safe = SafeMode.onStart();
        CheatConfig.load(!safe);
        Registry.registerAll();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (ServerCheats.host(server) == player) {
                Guard.run("Noclip safety", () -> SurvivalCheats.onHostJoin(server, player));
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player && LocalHost.is(player)) {
                Guard.run("Last Death", () -> Waypoints.setLastDeath(player, "Last Death"));
                // Death Chest (if on) then renames it "Death Chest" and moves your drops into a chest.
                com.minemenu.items.DeathChest.afterDeath(player);
                turnOff(com.minemenu.Feature.MORPH);
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, amount, blocked) ->
                com.minemenu.combat.CombatModules.afterDamage(entity, source, amount));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (LocalHost.is(player)) {
                turnOff(com.minemenu.Feature.MORPH); // a new dimension: back to normal
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ServerCheats.isPrivateSingleplayer(server)) {
                ServerTasks.tick(server);
                com.minemenu.build.BlockPlacer.tick();
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerTasks.clear();
            com.minemenu.build.BlockPlacer.clear();
            com.minemenu.build.UndoStack.clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> leaveWorld());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CheatConfig.save());
    }

    /** Kept for Death Chest (older code path); no longer read. */
    public static volatile boolean justMadeDeathChest;

    private static void turnOff(com.minemenu.Feature feature) {
        if (com.minemenu.CheatState.isOn(feature)) {
            com.minemenu.CheatState.toggle(feature);
        }
    }

    public static void onLeaveWorld(Runnable cleanup) {
        onLeaveWorld.add(cleanup);
    }

    private static void leaveWorld() {
        for (Runnable cleanup : onLeaveWorld) {
            Guard.run("world cleanup", cleanup);
        }
    }

    public static void clientTick(MinecraftClient client) {
        ticks++;
        SafeMode.tick(client);
        Guard.run("saving", () -> CheatConfig.tick(ticks));
        Guard.run("keys", () -> Keys.tick(client));
        Guard.run("flags", () -> {
            com.minemenu.player.PlayerModules.refreshFlags(client);
            com.minemenu.player.Morph.checkOff();
            com.minemenu.hud.HealthBars.refresh(client);
            com.minemenu.hud.HudModules.refresh(client);
        });
        Modules.clientTick(client);
    }
}
