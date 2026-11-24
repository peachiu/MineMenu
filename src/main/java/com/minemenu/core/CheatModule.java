package com.minemenu.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * One 3.4 hack. Server methods run on the integrated server thread (host player only); {@link #clientTick} runs
 * on the client thread. Every call goes through {@link Guard}, so an exception turns just this hack off.
 */
public interface CheatModule {
    default void enable(MinecraftServer server, ServerPlayerEntity player) {
    }

    /** {@code player} is null when the host already left (the world is closing). */
    default void disable(MinecraftServer server, @Nullable ServerPlayerEntity player) {
    }

    /** Every server tick while the hack is on. */
    default void serverTick(ServerPlayerEntity player, int tick) {
    }

    /** Every client tick while the hack is on. */
    default void clientTick(MinecraftClient client) {
    }

    /** A world was opened: forget anything from the previous one. */
    default void reset() {
    }
}
