package com.mineboost.tools;

import com.mineboost.BoostConfig;
import com.mineboost.PresetState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * A/B test of every boost, on your own PC: for each one, 4 s off then 4 s on, twice, measuring average FPS and
 * 1% low. Stand still looking at a busy spot while it runs (about 6 minutes). Results go to chat and to
 * config/minemeboost/benchmark-results.md as a table; your settings are put back at the end.
 */
public final class BoostAB {
    private record Test(String name, Consumer<MinecraftClient> on) {
    }

    private static final long SETTLE = 1_500_000_000L;
    private static final long MEASURE = 4_000_000_000L;
    private static List<Test> tests;
    private static PresetState baseline;
    private static boolean running;
    private static int index;
    private static int phase; // 0 off-settle,1 off-measure,2 on-settle,3 on-measure ... x2 rounds
    private static long phaseStart;
    private static final long[] frames = new long[50_000];
    private static int frameCount;
    private static long lastFrame;
    private static double[][] results; // [test][offAvg, offLow, onAvg, onLow] summed over rounds

    private BoostAB() {
    }

    public static boolean running() {
        return running;
    }

    private static List<Test> buildTests() {
        List<Test> list = new ArrayList<>();
        list.add(new Test("Render Scale 75%", c -> BoostConfig.get().renderScale = 75));
        list.add(new Test("Render Scale 50%", c -> BoostConfig.get().renderScale = 50));
        list.add(new Test("Sharpen at 75%", c -> { BoostConfig.get().renderScale = 75; BoostConfig.get().renderScaleSharpen = 50; }));
        list.add(new Test("Impostors 24", c -> BoostConfig.get().impostorDistance = 24));
        list.add(new Test("Simple Far Mobs 16", c -> BoostConfig.get().farMobDetail = 16));
        list.add(new Test("Max Entities 50", c -> BoostConfig.get().maxEntities = 50));
        list.add(new Test("Entity Distance 24", c -> BoostConfig.get().entityDistance = 3));
        list.add(new Test("Chests & Signs 16", c -> BoostConfig.get().blockEntityDistance = 2));
        list.add(new Test("Item Distance 6", c -> BoostConfig.get().itemDistance = 6));
        list.add(new Test("Simple Items", c -> BoostConfig.get().simpleItems = true));
        list.add(new Test("Smart Culling", c -> BoostConfig.get().smartCulling = true));
        list.add(new Test("Section Culling", c -> BoostConfig.get().sectionCulling = true));
        list.add(new Test("Max Particles 500", c -> BoostConfig.get().particleLimit = 500));
        list.add(new Test("Animated Textures off", c -> BoostConfig.get().animatedTextures = false));
        list.add(new Test("Graphics: Fast", c -> c.options.getGraphicsMode().setValue(GraphicsMode.FAST)));
        list.add(new Test("Clouds off", c -> c.options.getCloudRenderMode().setValue(CloudRenderMode.OFF)));
        list.add(new Test("Entity Shadows off", c -> c.options.getEntityShadows().setValue(false)));
        list.add(new Test("Smooth Lighting off", c -> c.options.getAo().setValue(false)));
        list.add(new Test("Render distance -4", c -> c.options.getViewDistance().setValue(Math.max(2, c.options.getViewDistance().getValue() - 4))));
        return list;
    }

    public static void start(MinecraftClient client) {
        tests = buildTests();
        baseline = PresetState.capture(client);
        results = new double[tests.size()][4];
        index = 0;
        phase = 0;
        phaseStart = System.nanoTime();
        running = true;
        applyOff(client);
    }

    private static void applyOff(MinecraftClient client) {
        baseline.restore(client);
        // The "off" side of each test is your settings with that one boost off.
        BoostConfig c = BoostConfig.get();
        switch (tests.get(index).name()) {
            case "Render Scale 75%", "Render Scale 50%" -> c.renderScale = 100;
            case "Sharpen at 75%" -> { // the cost of sharpening: 75% without it vs 75% with it
                c.renderScale = 75;
                c.renderScaleSharpen = 0;
            }
            case "Impostors 24" -> c.impostorDistance = 0;
            case "Simple Far Mobs 16" -> c.farMobDetail = -1;
            case "Max Entities 50" -> c.maxEntities = -1;
            case "Entity Distance 24" -> c.entityDistance = BoostConfig.ENTITY_DISTANCE_UNLIMITED;
            case "Chests & Signs 16" -> c.blockEntityDistance = BoostConfig.BLOCK_ENTITY_DISTANCE_VANILLA;
            case "Item Distance 6" -> c.itemDistance = -1;
            case "Simple Items" -> c.simpleItems = false;
            case "Smart Culling" -> c.smartCulling = false;
            case "Section Culling" -> c.sectionCulling = false;
            case "Max Particles 500" -> c.particleLimit = -1;
            case "Animated Textures off" -> c.animatedTextures = true;
            case "Graphics: Fast" -> client.options.getGraphicsMode().setValue(GraphicsMode.FANCY);
            case "Clouds off" -> client.options.getCloudRenderMode().setValue(CloudRenderMode.FANCY);
            case "Entity Shadows off" -> client.options.getEntityShadows().setValue(true);
            case "Smooth Lighting off" -> client.options.getAo().setValue(true);
            default -> {
            }
        }
    }

    /** Called every frame (HUD callback). */
    public static void onFrame(DrawContext context) {
        if (!running) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            finish(client, false);
            return;
        }
        long now = System.nanoTime();
        boolean measuring = phase % 2 == 1;
        if (measuring && lastFrame != 0 && frameCount < frames.length) {
            frames[frameCount++] = now - lastFrame;
        }
        lastFrame = now;
        long length = measuring ? MEASURE : SETTLE * (tests.get(index).name().startsWith("Render distance") ? 2 : 1);
        if (now - phaseStart >= length) {
            if (measuring) {
                double[] stats = stats();
                int side = (phase / 2) % 2; // 0 = off, 1 = on
                results[index][side * 2] += stats[0] / 2;
                results[index][side * 2 + 1] += stats[1] / 2;
            }
            phase++;
            frameCount = 0;
            lastFrame = 0;
            phaseStart = now;
            if (phase == 8) {
                phase = 0;
                index++;
                if (index >= tests.size()) {
                    finish(client, true);
                    return;
                }
                applyOff(client);
            } else if (phase == 2 || phase == 6) {
                tests.get(index).on().accept(client);
            } else if (phase == 4) {
                applyOff(client);
            }
        }
        Text text = Text.literal(String.format("MineBoost A/B test %d/%d: %s (%s) - stand still, look at a busy spot",
                index + 1, tests.size(), tests.get(index).name(), (phase / 2) % 2 == 0 ? "off" : "on")).formatted(Formatting.GREEN);
        context.drawCenteredTextWithShadow(client.textRenderer, text, context.getScaledWindowWidth() / 2, 54, 0xFFFFFF);
    }

    private static double[] stats() {
        if (frameCount < 5) {
            return new double[]{0, 0};
        }
        long[] sorted = Arrays.copyOf(frames, frameCount);
        long total = 0;
        for (long t : sorted) {
            total += t;
        }
        Arrays.sort(sorted);
        int slow = Math.max(1, frameCount / 100);
        long slowTotal = 0;
        for (int i = frameCount - slow; i < frameCount; i++) {
            slowTotal += sorted[i];
        }
        return new double[]{frameCount * 1e9 / total, slow * 1e9 / slowTotal};
    }

    public static void cancel(MinecraftClient client) {
        if (running) {
            finish(client, false);
        }
    }

    private static void finish(MinecraftClient client, boolean complete) {
        running = false;
        if (baseline != null) {
            baseline.restore(client);
        }
        if (!complete || client.player == null) {
            return;
        }
        StringBuilder md = new StringBuilder("# MineBoost A/B results\n\n")
                .append("Each boost: 4 s off, 4 s on, twice; your other settings unchanged.\n\n")
                .append("| Boost | Avg FPS off | Avg FPS on | Gain | 1% low off | 1% low on |\n|---|---|---|---|---|---|\n");
        List<String> best = new ArrayList<>();
        for (int i = 0; i < tests.size(); i++) {
            double[] r = results[i];
            double gain = r[0] > 0 ? (r[2] - r[0]) * 100 / r[0] : 0;
            md.append(String.format("| %s | %.1f | %.1f | %+.0f%% | %.1f | %.1f |\n", tests.get(i).name(), r[0], r[2], gain, r[1], r[3]));
            best.add(String.format("%+.0f%% %s", gain, tests.get(i).name()));
            BoostConfig.get().abGains.put(tests.get(i).name(), (int) Math.round(gain)); // presets read the Render Scale ones
        }
        BoostConfig.save();
        best.sort((a, b) -> Double.compare(Double.parseDouble(b.substring(0, b.indexOf('%'))), Double.parseDouble(a.substring(0, a.indexOf('%')))));
        Path file = FabricLoader.getInstance().getConfigDir().resolve("minemeboost").resolve("benchmark-results.md");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, md.toString());
        } catch (Exception ignored) {
        }
        client.player.sendMessage(Text.literal("MineBoost A/B done. Best on your PC: " + String.join(", ", best.subList(0, Math.min(5, best.size())))
                + ". Full table: config/minemeboost/benchmark-results.md").formatted(Formatting.GREEN), false);
    }
}
