package com.minemenu.esp;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.MineMenuClient;
import com.minemenu.core.Bootstrap;
import com.minemenu.core.CheatModule;
import com.minemenu.core.FeatureSettings;
import com.minemenu.core.Modules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/** Ore Vision and Chest ESP (client side since 3.4: line boxes, scanned off the main thread). */
public final class EspModules {
    /** Chest positions and colours, as pairs (pos, colour). Replaced as a whole, read by the render thread. */
    private static volatile long[] chests = new long[0];
    private static int ticks;
    /** Set by MineMeBoostXray: Ore Vision and Chest ESP are shortcuts to MineXray, which draws them instead. */
    public static volatile boolean delegated;

    private EspModules() {
    }

    public static void register() {
        Modules.register(Feature.ORE_VISION, new CheatModule() {
            @Override
            public void clientTick(MinecraftClient client) {
                if (!delegated && ++ticks % 5 == 0) {
                    SectionScanner.tick(client, (int) FeatureSettings.get(Feature.ORE_VISION, "radius"));
                }
            }

            @Override
            public void reset() {
                SectionScanner.clear();
            }
        });
        Modules.register(Feature.CHEST_ESP, new CheatModule() {
            @Override
            public void clientTick(MinecraftClient client) {
                if (!delegated && ++ticks % 20 == 0) {
                    scanChests(client, (int) FeatureSettings.get(Feature.CHEST_ESP, "radius"));
                }
            }

            @Override
            public void reset() {
                chests = new long[0];
            }
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> SectionScanner.forgetChunk(chunk.getPos().x, chunk.getPos().z));
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> SectionScanner.forgetChunk(chunk.getPos().x, chunk.getPos().z));
        Bootstrap.onLeaveWorld(() -> {
            SectionScanner.clear();
            chests = new long[0];
        });
        LineRenderer.register((sink, camera) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (delegated || !MineMenuClient.isSingleplayer(client)) {
                return;
            }
            BlockPos center = BlockPos.ofFloored(camera);
            if (CheatState.isOn(Feature.ORE_VISION)) {
                SectionScanner.forEach(center, (int) FeatureSettings.get(Feature.ORE_VISION, "radius"), sink::block);
            } else if (SectionScanner.cachedSections() > 0) {
                SectionScanner.clear(); // turned off: free the cache
            }
            if (CheatState.isOn(Feature.CHEST_ESP)) {
                long[] list = chests;
                for (int i = 0; i < list.length; i += 2) {
                    sink.block(BlockPos.unpackLongX(list[i]), BlockPos.unpackLongY(list[i]), BlockPos.unpackLongZ(list[i]), (int) list[i + 1]);
                }
            }
        });
    }

    private static int chestColor(BlockEntity blockEntity) {
        if (blockEntity instanceof EnderChestBlockEntity) return 0x55FFFF;
        if (blockEntity instanceof ShulkerBoxBlockEntity) return 0xFF55FF;
        if (blockEntity instanceof BarrelBlockEntity) return 0xC08040;
        if (blockEntity instanceof ChestBlockEntity) return 0xFFAA00;
        return -1;
    }

    /** Client thread, once a second: copies the chest positions of nearby chunks (small lists). */
    private static void scanChests(MinecraftClient client, int radius) {
        if (client.world == null || client.player == null) {
            return;
        }
        BlockPos center = client.player.getBlockPos();
        int r = (radius >> 4) + 1;
        long[] buffer = new long[64];
        int n = 0;
        for (int cx = (center.getX() >> 4) - r; cx <= (center.getX() >> 4) + r; cx++) {
            for (int cz = (center.getZ() >> 4) - r; cz <= (center.getZ() >> 4) + r; cz++) {
                if (!client.world.getChunkManager().isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (BlockEntity blockEntity : client.world.getChunk(cx, cz).getBlockEntities().values()) {
                    int color = chestColor(blockEntity);
                    if (color >= 0 && blockEntity.getPos().getSquaredDistance(center) <= (double) radius * radius) {
                        if (n + 2 > buffer.length) {
                            buffer = java.util.Arrays.copyOf(buffer, buffer.length * 2);
                        }
                        buffer[n++] = blockEntity.getPos().asLong();
                        buffer[n++] = color;
                    }
                }
            }
        }
        chests = java.util.Arrays.copyOf(buffer, n);
    }

    /** For tests: how many chests Chest ESP shows. */
    public static int chestCount() {
        return chests.length / 2;
    }
}
