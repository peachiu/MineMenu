package com.minemenu;

import net.minecraft.client.MinecraftClient;

/** Short way to the client from inner classes of screens. */
final class MinecraftClientHolder {
    private MinecraftClientHolder() {
    }

    static MinecraftClient get() {
        return MinecraftClient.getInstance();
    }
}
