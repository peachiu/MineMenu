package com.mineboost.doctor;

import com.mineboost.BoostConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.profiler.ProfileResult;
import net.minecraft.util.profiler.ProfilerTiming;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lag Doctor: for 30 or 60 seconds of normal play, it switches on Minecraft's own profiler (the one behind the
 * F3 pie chart, without showing it) and adds up how long each part of every frame takes: entities, block
 * entities, particles, terrain, chunk rebuilding, HUD, waiting for the graphics card, plus garbage collection
 * and the singleplayer server's tick. Then it ranks what costs the most and offers a fix for each.
 */
public final class LagDoctor {
    /** Profiler section name (last part of its path) -> what it means. */
    private static final Map<String, String> SECTIONS = Map.ofEntries(
            Map.entry("entities", "Drawing mobs and other entities"),
            Map.entry("blockentities", "Drawing chests, signs and other block entities"),
            Map.entry("particles", "Particles"),
            Map.entry("terrain", "Drawing terrain (render distance, graphics)"),
            Map.entry("terrain_setup", "Working out which chunks to draw"),
            Map.entry("compile_sections", "Rebuilding chunks"),
            Map.entry("upload", "Rebuilding chunks"),
            Map.entry("build_near_sync", "Rebuilding chunks"),
            Map.entry("translucent", "Water, glass and other see-through blocks"),
            Map.entry("sky", "Sky"),
            Map.entry("clouds", "Clouds"),
            Map.entry("weather", "Rain and snow"),
            Map.entry("outline", "Glowing outlines"),
            Map.entry("gui", "HUD and menus"),
            Map.entry("updateDisplay", "Waiting for the graphics card"),
            Map.entry("tick", "Game logic on your side (client tick)"));

    private static boolean recording;
    private static long endAt;
    private static int frames;
    private static long totalNanos;
    private static final Map<String, Double> nanosBySection = new HashMap<>();
    private static long gcStartMillis;
    private static Report lastReport;

    private LagDoctor() {
    }

    public static boolean recording() {
        return recording;
    }

    public static Report lastReport() {
        return lastReport;
    }

    public static void start(int seconds) {
        recording = true;
        endAt = System.nanoTime() + seconds * 1_000_000_000L;
        frames = 0;
        totalNanos = 0;
        nanosBySection.clear();
        gcStartMillis = gcMillis();
    }

    private static long gcMillis() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, bean.getCollectionTime());
        }
        return total;
    }

    /**
     * Called with the profiler result after each frame (MinecraftClient.endMonitor). The game's profiler adds up
     * everything since it was switched on, so only the last result counts: its percentages cover the whole
     * recording, and its tick span is the number of frames.
     */
    public static void onFrame(ProfileResult result) {
        if (!recording || result == null) {
            return;
        }
        if (System.nanoTime() >= endAt) {
            recording = false;
            nanosBySection.clear();
            frames = Math.max(1, result.getTickSpan());
            totalNanos = result.getTimeSpan();
            collect(result, "root", totalNanos, 0);
            lastReport = buildReport(MinecraftClient.getInstance());
            MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(new LagDoctorScreen(null)));
        }
    }

    private static void collect(ProfileResult result, String path, long span, int depth) {
        if (depth > 5) {
            return;
        }
        List<ProfilerTiming> timings = result.getTimings(path);
        for (int i = 1; i < timings.size(); i++) { // [0] is the parent itself
            ProfilerTiming timing = timings.get(i);
            if ("unspecified".equals(timing.name)) {
                continue;
            }
            if (SECTIONS.containsKey(timing.name)) {
                nanosBySection.merge(SECTIONS.get(timing.name), timing.totalUsagePercentage / 100.0 * span, Double::sum);
            }
            collect(result, path + ProfileResult.SPLITTER_CHAR + timing.name, span, depth + 1);
        }
    }

    /** One line of the report: what, how many ms per frame, and a fix (or null). */
    public record Finding(String text, double msPerFrame, String fixLabel, Runnable fix) {
    }

    public record Report(int frames, double avgFps, double avgMs, List<Finding> findings) {
    }

    private static Report buildReport(MinecraftClient client) {
        double frameMs = frames == 0 ? 0 : totalNanos / 1e6 / frames;
        List<Finding> findings = new ArrayList<>();
        BoostConfig c = BoostConfig.get();
        String entityDetail = entityDetail(client);
        for (Map.Entry<String, Double> entry : nanosBySection.entrySet()) {
            double ms = entry.getValue() / 1e6 / Math.max(1, frames);
            if (ms < 0.2) {
                continue;
            }
            String name = entry.getKey();
            String text = name;
            String fixLabel = null;
            Runnable fix = null;
            switch (name) {
                case "Drawing mobs and other entities" -> {
                    text = name + " (" + entityDetail + ")";
                    if (c.maxEntities < 0) {
                        fixLabel = "Max Entities 100";
                        fix = () -> c.maxEntities = 100;
                    } else if (c.farMobDetail < 0) {
                        fixLabel = "Simple Far Mobs 16";
                        fix = () -> c.farMobDetail = 16;
                    } else {
                        fixLabel = "Entity Distance 48";
                        fix = () -> c.entityDistance = 6;
                    }
                }
                case "Drawing chests, signs and other block entities" -> {
                    fixLabel = "Chests & Signs 32";
                    fix = () -> c.blockEntityDistance = 4;
                }
                case "Particles" -> {
                    fixLabel = "Max Particles 1000";
                    fix = () -> c.particleLimit = 1000;
                }
                case "Drawing terrain (render distance, graphics)", "Working out which chunks to draw", "Rebuilding chunks" -> {
                    fixLabel = "Render distance -2";
                    fix = () -> client.options.getViewDistance().setValue(Math.max(2, client.options.getViewDistance().getValue() - 2));
                }
                case "Waiting for the graphics card" -> {
                    text = name + ": your graphics card is the limit";
                    fixLabel = "Render Scale 75%";
                    fix = () -> c.renderScale = 75;
                }
                case "Water, glass and other see-through blocks" -> {
                    fixLabel = "Graphics: Fast";
                    fix = () -> client.options.getGraphicsMode().setValue(net.minecraft.client.option.GraphicsMode.FAST);
                }
                case "Clouds" -> {
                    fixLabel = "Clouds off";
                    fix = () -> client.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
                }
                case "Rain and snow" -> {
                    fixLabel = "Hide Rain & Snow";
                    fix = () -> c.hideWeather = true;
                }
                default -> {
                }
            }
            findings.add(new Finding(text, ms, fixLabel, fix));
        }
        double gcMs = (gcMillis() - gcStartMillis) / (double) Math.max(1, frames);
        if (gcMs >= 0.2) {
            findings.add(new Finding("Java cleaning up memory (garbage collection)", gcMs, "JVM Advisor", null));
        }
        IntegratedServer server = client.getServer();
        if (server != null) {
            double mspt = server.getAverageNanosPerTick() / 1e6;
            if (mspt > 30) {
                findings.add(new Finding(String.format("The singleplayer world itself is slow: %.0f ms per tick (50 is the limit)", mspt),
                        mspt / 5, "Simulation distance -2",
                        () -> client.options.getSimulationDistance().setValue(Math.max(5, client.options.getSimulationDistance().getValue() - 2))));
            }
        }
        findings.sort((a, b) -> Double.compare(b.msPerFrame(), a.msPerFrame()));
        return new Report(frames, frameMs > 0 ? 1000 / frameMs : 0, frameMs, findings);
    }

    /** "340 entities within 32 blocks, most: chicken x210". */
    private static String entityDetail(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return "";
        }
        Map<EntityType<?>, Integer> counts = new HashMap<>();
        int total = 0;
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof LivingEntity && entity != client.player && entity.squaredDistanceTo(client.player) < 32 * 32) {
                counts.merge(entity.getType(), 1, Integer::sum);
                total++;
            }
        }
        EntityType<?> most = null;
        int mostCount = 0;
        for (Map.Entry<EntityType<?>, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > mostCount) {
                most = entry.getKey();
                mostCount = entry.getValue();
            }
        }
        return total + " mobs within 32 blocks" + (most == null ? "" : ", most: " + most.getName().getString() + " x" + mostCount);
    }

    /** HUD while recording. */
    public static void renderHud(DrawContext context) {
        if (!recording) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int left = (int) Math.ceil((endAt - System.nanoTime()) / 1e9);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Lag Doctor: " + left + "s, keep playing normally")
                .formatted(Formatting.AQUA), context.getScaledWindowWidth() / 2, 42, 0xFFFFFF);
    }
}
