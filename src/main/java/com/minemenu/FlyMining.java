package com.minemenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Decides when mining in the air should be as fast as on the ground: Fly is on, and it's you. */
public final class FlyMining {
    private FlyMining() {
    }

    public static boolean applies(PlayerEntity player) {
        if (!CheatState.isOn(Feature.FLY)) {
            return false;
        }
        // The server and client both check how fast you mine, so both must agree.
        if (player instanceof ServerPlayerEntity serverPlayer) {
            return serverPlayer.getServer() != null && ServerCheats.host(serverPlayer.getServer()) == serverPlayer;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return player == client.player && MineMenuClient.isSingleplayer(client);
    }
}
