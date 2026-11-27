package com.minemenu.hud;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.MineMenuClient;
import com.minemenu.core.Bootstrap;
import com.minemenu.core.CheatModule;
import com.minemenu.core.FeatureSettings;
import com.minemenu.core.Guard;
import com.minemenu.core.KeyBinds;
import com.minemenu.core.Modules;
import com.minemenu.esp.LineRenderer;
import com.minemenu.fun.FunModules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Utility hacks: Minimap, Info HUD, Light Level Overlay, Chunk Borders, Slime Chunks, and the Chaos countdown. */
public final class HudModules {
    private static boolean chunkBordersShown;
    /** Spawnable blocks found by the light scan: pos and colour pairs. */
    private static volatile long[] spawnSpots = new long[0];
    private static int lightTicks;

    private HudModules() {
    }

    public static void register() {
        Modules.register(Feature.MINIMAP, new CheatModule() {
            @Override
            public void clientTick(MinecraftClient client) {
                Minimap.tick(client);
                while (KeyBinds.bigMap.wasPressed()) {
                    client.setScreen(new BigMapScreen());
                }
            }

            @Override
            public void reset() {
                Minimap.release();
            }
        });
        Modules.register(Feature.INFO_HUD, new CheatModule() { });
        Modules.register(Feature.CHUNK_BORDERS, new CheatModule() {
            @Override
            public void clientTick(MinecraftClient client) {
                if (!chunkBordersShown) {
                    chunkBordersShown = client.debugRenderer.toggleShowChunkBorder();
                }
            }
        });
        Modules.register(Feature.LIGHT_OVERLAY, new CheatModule() {
            @Override
            public void clientTick(MinecraftClient client) {
                if (++lightTicks % 10 == 0) {
                    scanLight(client);
                }
            }
        });
        Modules.register(Feature.SLIME_CHUNKS, new CheatModule() { });
        Modules.register(Feature.MOB_HEALTH_BARS, new CheatModule() { });

        HudRenderCallback.EVENT.register((context, tickCounter) -> Guard.run("Utility HUD", () -> renderHud(context)));
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            MapColors.forget(chunk.getPos().x, chunk.getPos().z);
            Minimap.markDirty();
        });
        Bootstrap.onLeaveWorld(() -> {
            Minimap.release();
            spawnSpots = new long[0];
        });
        LineRenderer.register((sink, camera) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            // 5.0: light levels also on servers (read from the world you see); slime chunks need the seed (your world only).
            if (client.world == null || !(MineMenuClient.isSingleplayer(client) || Feature.LIGHT_OVERLAY.allowedInMultiplayer())) {
                return;
            }
            if (CheatState.isOn(Feature.LIGHT_OVERLAY)) {
                long[] spots = spawnSpots;
                for (int i = 0; i < spots.length; i += 2) {
                    int x = BlockPos.unpackLongX(spots[i]);
                    int y = BlockPos.unpackLongY(spots[i]);
                    int z = BlockPos.unpackLongZ(spots[i]);
                    int color = (int) spots[i + 1];
                    sink.line(x + 0.1, y + 0.02, z + 0.1, x + 0.9, y + 0.02, z + 0.9, color);
                    sink.line(x + 0.9, y + 0.02, z + 0.1, x + 0.1, y + 0.02, z + 0.9, color);
                }
            }
            if (CheatState.isOn(Feature.SLIME_CHUNKS) && client.world.getRegistryKey() == World.OVERWORLD) {
                IntegratedServer server = client.getServer();
                if (server == null) {
                    return;
                }
                long seed = server.getOverworld().getSeed();
                ChunkPos here = client.player.getChunkPos();
                double y = Math.floor(client.player.getY()) + 0.05;
                for (int cx = here.x - 4; cx <= here.x + 4; cx++) {
                    for (int cz = here.z - 4; cz <= here.z + 4; cz++) {
                        if (ChunkRandom.getSlimeRandom(cx, cz, seed, 987234911L).nextInt(10) == 0) {
                            sink.box(cx * 16 + 0.05, y, cz * 16 + 0.05, cx * 16 + 15.95, y + 0.02, cz * 16 + 15.95, 0x55FF55);
                        }
                    }
                }
            }
        });
    }

    /** Called every client tick (also when the hacks are off, to switch Chunk Borders back). */
    public static void refresh(MinecraftClient client) {
        boolean allowed = MineMenuClient.isSingleplayer(client) || Feature.CHUNK_BORDERS.allowedInMultiplayer();
        if (chunkBordersShown && (!CheatState.isOn(Feature.CHUNK_BORDERS) || !allowed || client.world == null)) {
            chunkBordersShown = client.debugRenderer.toggleShowChunkBorder();
        }
    }

    /** Blocks within 16 where mobs can spawn in the dark: solid top, air above, block light 0. Red = also dark sky. */
    private static void scanLight(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }
        BlockPos center = client.player.getBlockPos();
        long[] buffer = new long[256];
        int n = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable below = new BlockPos.Mutable();
        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                for (int dy = -8; dy <= 8; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    below.set(pos.getX(), pos.getY() - 1, pos.getZ());
                    var state = client.world.getBlockState(pos);
                    if (!state.isAir() || !client.world.getBlockState(below).isSideSolidFullSquare(client.world, below, net.minecraft.util.math.Direction.UP)
                            || client.world.getLightLevel(LightType.BLOCK, pos) > 0) {
                        continue;
                    }
                    int color = client.world.getLightLevel(LightType.SKY, pos) <= 7 ? 0xFF3030 : 0xFFFF40;
                    if (n + 2 > buffer.length) {
                        buffer = java.util.Arrays.copyOf(buffer, buffer.length * 2);
                    }
                    buffer[n++] = pos.asLong();
                    buffer[n++] = color;
                }
            }
        }
        spawnSpots = java.util.Arrays.copyOf(buffer, n);
    }

    private static void renderHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        boolean singleplayer = MineMenuClient.isSingleplayer(client);
        if (CheatState.isOn(Feature.MINIMAP)) {
            Minimap.render(context, client);
        }
        if (CheatState.isOn(Feature.INFO_HUD) && !client.getDebugHud().shouldShowDebugHud()) {
            List<String> lines = infoLines(client);
            int y = context.getScaledWindowHeight() - 60 - lines.size() * 10;
            for (String line : lines) {
                context.drawTextWithShadow(client.textRenderer, Text.literal(line), 4, y, 0xFFFFFF);
                y += 10;
            }
        }
        int chaos = FunModules.chaosTicksLeft;
        if (singleplayer && CheatState.isOn(Feature.CHAOS_MODE) && chaos > 0) {
            int seconds = chaos / 20;
            context.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("minemenu.hud.chaos_in", String.format("%d:%02d", seconds / 60, seconds % 60))
                    .formatted(Formatting.LIGHT_PURPLE), context.getScaledWindowWidth() / 2, 4, 0xFFFFFF);
        }
    }

    private static List<String> infoLines(MinecraftClient client) {
        List<String> lines = new ArrayList<>();
        var p = client.player;
        if (FeatureSettings.is(Feature.INFO_HUD, "coords")) {
            lines.add(String.format("§7XYZ §f%d %d %d", p.getBlockX(), p.getBlockY(), p.getBlockZ()));
        }
        if (FeatureSettings.is(Feature.INFO_HUD, "direction")) {
            String[] names = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};
            int index = Math.floorMod(Math.round(MathHelper.wrapDegrees(p.getYaw()) / 45f), 8);
            lines.add("§7" + com.minemenu.core.Tr.get("minemenu.hud.facing", "Facing") + " §f" + com.minemenu.core.Tr.get("minemenu.hud.dir." + names[index], names[index]));
        }
        if (FeatureSettings.is(Feature.INFO_HUD, "biome")) {
            lines.add("§7" + com.minemenu.core.Tr.get("minemenu.hud.biome", "Biome") + " §f" + client.world.getBiome(p.getBlockPos()).getKey().map(k -> k.getValue().getPath().replace('_', ' ')).orElse("?"));
        }
        long time = client.world.getTimeOfDay();
        if (FeatureSettings.is(Feature.INFO_HUD, "time")) {
            int minutes = (int) ((time % 24000 / 1000.0 + 6) * 60) % (24 * 60);
            lines.add(String.format("§7%s §f%02d:%02d", com.minemenu.core.Tr.get("minemenu.hud.time", "Time"), minutes / 60, minutes % 60));
        }
        if (FeatureSettings.is(Feature.INFO_HUD, "day")) {
            lines.add("§7" + com.minemenu.core.Tr.get("minemenu.hud.day", "Day") + " §f" + (time / 24000 + 1));
        }
        if (FeatureSettings.is(Feature.INFO_HUD, "speed")) {
            double speed = p.getVelocity().multiply(1, 0, 1).length() * 20;
            lines.add(String.format("§7%s §f%.1f m/s", com.minemenu.core.Tr.get("minemenu.hud.speed", "Speed"), speed));
        }
        if (FeatureSettings.is(Feature.INFO_HUD, "chunk")) {
            lines.add("§7" + com.minemenu.core.Tr.get("minemenu.hud.chunk", "Chunk") + " §f" + p.getChunkPos().x + ", " + p.getChunkPos().z);
        }
        if (FeatureSettings.is(Feature.INFO_HUD, "light") && client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit) {
            BlockPos at = hit.getBlockPos().offset(hit.getSide());
            lines.add("§7" + com.minemenu.core.Tr.get("minemenu.hud.light", "Light") + " §f" + client.world.getLightLevel(LightType.BLOCK, at) + " §7(" + com.minemenu.core.Tr.get("minemenu.hud.sky", "sky") + " " + client.world.getLightLevel(LightType.SKY, at) + ")");
        }
        return lines;
    }
}
