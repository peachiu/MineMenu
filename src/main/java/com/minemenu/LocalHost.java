package com.minemenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * "Is this entity you?" for hacks that change movement or physics. Movement is worked out on both the
 * client and the server, so both copies of your player must get the same answer. Only ever true in a
 * private singleplayer world.
 */
public final class LocalHost {
    private LocalHost() {
    }

    public static boolean is(Entity entity) {
        if (entity instanceof ServerPlayerEntity serverPlayer) {
            return serverPlayer.getServer() != null && ServerCheats.host(serverPlayer.getServer()) == serverPlayer;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return entity != null && entity == client.player && MineMenuClient.isSingleplayer(client);
    }
}
