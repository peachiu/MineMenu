package com.minemenu.core;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.MineMenuClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Which {@link CheatModule} runs each 3.4 hack, and the guarded calls into them. */
public final class Modules {
    private static final Map<Feature, CheatModule> MODULES = new EnumMap<>(Feature.class);

    private Modules() {
    }

    public static void register(Feature feature, CheatModule module) {
        MODULES.put(feature, module);
    }

    public static boolean has(Feature feature) {
        return MODULES.containsKey(feature);
    }

    public static CheatModule get(Feature feature) {
        return MODULES.get(feature);
    }

    /** Returns false if this hack has no module (then the 3.3 code handles it). */
    public static boolean enable(MinecraftServer server, ServerPlayerEntity player, Feature feature) {
        CheatModule module = MODULES.get(feature);
        if (module == null) {
            return false;
        }
        Guard.run(feature, () -> module.enable(server, player));
        return true;
    }

    public static boolean disable(MinecraftServer server, ServerPlayerEntity player, Feature feature) {
        CheatModule module = MODULES.get(feature);
        if (module == null) {
            return false;
        }
        Guard.run(feature, () -> module.disable(server, player));
        return true;
    }

    public static void serverTick(ServerPlayerEntity player, Set<Feature> applied, int tick) {
        for (Feature feature : applied) {
            CheatModule module = MODULES.get(feature);
            if (module != null) {
                Guard.run(feature, () -> module.serverTick(player, tick));
            }
        }
    }

    /** Client side: only in singleplayer, except the few hacks that are fair on servers too. */
    public static void clientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        boolean singleplayer = MineMenuClient.isSingleplayer(client);
        for (Feature feature : CheatState.enabled()) {
            CheatModule module = MODULES.get(feature);
            if (module != null && (singleplayer || feature.allowedInMultiplayer())) {
                Guard.run(feature, () -> module.clientTick(client));
            }
        }
    }

    public static void resetAll() {
        for (Map.Entry<Feature, CheatModule> entry : MODULES.entrySet()) {
            Guard.run(entry.getKey(), entry.getValue()::reset);
        }
    }
}
