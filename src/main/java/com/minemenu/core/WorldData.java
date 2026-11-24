package com.minemenu.core;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;

/** Small per-world data (loadouts, inventory snapshots...): minemenu_data.dat in the world folder. Server thread. */
public final class WorldData {
    private WorldData() {
    }

    private static Path file(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("minemenu_data.dat");
    }

    public static NbtCompound read(MinecraftServer server) {
        try {
            Path file = file(server);
            if (Files.exists(file)) {
                return NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
            }
        } catch (Exception ignored) {
        }
        return new NbtCompound();
    }

    public static void write(MinecraftServer server, NbtCompound data) {
        try {
            NbtIo.writeCompressed(data, file(server));
        } catch (Exception ignored) {
        }
    }
}
