package com.mineboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A 10 second FPS benchmark: average FPS, 1% low and the worst frame, posted in chat. */
public final class Benchmark {
    private static final long DURATION_NANOS = 10_000_000_000L;
    private static final int MAX_FRAMES = 20_000;

    private static boolean running;
    private static long start;
    private static long lastFrame;
    private static final long[] frameTimes = new long[MAX_FRAMES];
    private static int frames;

    private Benchmark() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static void start() {
        running = true;
        start = 0; // starts on the first frame after the menu closes
        frames = 0;
    }

    /** Called once per rendered frame (HUD render callback). */
    public static void onFrame(DrawContext context) {
        if (!running) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.nanoTime();
        if (start == 0) {
            start = now;
            lastFrame = now;
            return;
        }
        if (frames < MAX_FRAMES) {
            frameTimes[frames++] = now - lastFrame;
        }
        lastFrame = now;

        long elapsed = now - start;
        if (elapsed >= DURATION_NANOS) {
            running = false;
            report(client, elapsed);
            return;
        }
        int secondsLeft = (int) Math.ceil((DURATION_NANOS - elapsed) / 1e9);
        Text text = Text.translatable("mineboost.text.mineboost_benchmark_ss_keep_playing_normally", secondsLeft)
                .formatted(Formatting.GREEN);
        context.drawCenteredTextWithShadow(client.textRenderer, text, context.getScaledWindowWidth() / 2, 30, 0xFFFFFF);
    }

    private static void report(MinecraftClient client, long elapsed) {
        if (client.player == null || frames == 0) {
            return;
        }
        long[] sorted = Arrays.copyOf(frameTimes, frames);
        Arrays.sort(sorted);
        int avg = (int) Math.round(frames / (elapsed / 1e9));
        int slowest = Math.max(1, frames / 100);
        long slowTotal = 0;
        for (int i = frames - slowest; i < frames; i++) {
            slowTotal += sorted[i];
        }
        int low = (int) Math.round(slowest * 1e9 / slowTotal);
        int worst = (int) Math.round(1e9 / sorted[frames - 1]);

        String preset = Tr.get("mineboost.benchmark.custom", "Custom");
        for (Preset p : Preset.values()) {
            if (p.isActive(client)) {
                preset = p.displayName();
            }
        }
        client.player.sendMessage(Text.translatable("mineboost.text.mineboost_benchmark").formatted(Formatting.GREEN)
                .append(Text.translatable("mineboost.text.avg_s_fps", avg).formatted(Formatting.WHITE, Formatting.BOLD))
                .append(Text.translatable("mineboost.text.1_low_s_worst_frame_s", low, worst).formatted(Formatting.WHITE))
                .append(Text.translatable("mineboost.text.s_render_distance_s", preset, client.options.getViewDistance().getValue()).formatted(Formatting.GRAY)), false);

        Result result = new Result();
        result.avg = avg;
        result.low = low;
        result.preset = preset;
        result.settings = snapshot(client);
        Result last = BoostConfig.get().lastBenchmark;
        if (last != null && last.settings != null && last.avg > 0) {
            client.player.sendMessage(compare(last, result), false);
        }
        BoostConfig.get().lastBenchmark = result;
        BoostConfig.save();
    }

    /** "Before vs after": the FPS difference to the last benchmark, and which settings changed in between. */
    private static Text compare(Result before, Result after) {
        MutableText text = Text.translatable("mineboost.text.vs_last_run").formatted(Formatting.GRAY)
                .append(diff(Tr.get("mineboost.benchmark.avg", "avg"), before.avg, after.avg))
                .append(Text.literal(", ").formatted(Formatting.GRAY))
                .append(diff(Tr.get("mineboost.benchmark.low", "1% low"), before.low, after.low));
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> entry : after.settings.entrySet()) {
            String old = before.settings.get(entry.getKey());
            if (old != null && !old.equals(entry.getValue())) {
                changed.add(Tr.auto("mineboost.setting_name.", entry.getKey()) + " " + Tr.auto("mineboost.value.", old) + " → " + Tr.auto("mineboost.value.", entry.getValue()));
            }
        }
        if (changed.isEmpty()) {
            text.append(Text.translatable("mineboost.text.same_settings_so_this_is_just").formatted(Formatting.GRAY));
        } else {
            int shown = Math.min(5, changed.size());
            text.append(Text.translatable("mineboost.text.changed").formatted(Formatting.GRAY))
                    .append(Text.literal(String.join(", ", changed.subList(0, shown))
                            + (changed.size() > shown ? " " + Tr.format("mineboost.benchmark.more", "and %s more", changed.size() - shown) : ""))
                            .formatted(Formatting.YELLOW));
        }
        return text;
    }

    private static Text diff(String name, int before, int after) {
        int percent = before > 0 ? (int) Math.round((after - before) * 100.0 / before) : 0;
        Formatting color = percent >= 3 ? Formatting.GREEN : percent <= -3 ? Formatting.RED : Formatting.WHITE;
        return Text.literal(name + " " + before + " → " + after + " (" + (percent >= 0 ? "+" : "") + percent + "%)")
                .formatted(color);
    }

    /** Every setting on the MineBoost screen, by name, as shown text. */
    static Map<String, String> snapshot(MinecraftClient client) {
        GameOptions o = client.options;
        BoostConfig c = BoostConfig.get();
        Map<String, String> s = new LinkedHashMap<>();
        s.put("Render Distance", String.valueOf(o.getViewDistance().getValue()));
        s.put("Simulation Distance", String.valueOf(o.getSimulationDistance().getValue()));
        s.put("Graphics", String.valueOf(o.getGraphicsMode().getValue()));
        s.put("Max FPS", String.valueOf(o.getMaxFps().getValue()));
        s.put("VSync", onOff(o.getEnableVsync().getValue()));
        s.put("Smooth Lighting", onOff(o.getAo().getValue()));
        s.put("Clouds", String.valueOf(o.getCloudRenderMode().getValue()));
        s.put("Particles", String.valueOf(o.getParticles().getValue()));
        s.put("Entity Shadows", onOff(o.getEntityShadows().getValue()));
        s.put("Entity Distance Scaling", String.valueOf(o.getEntityDistanceScaling().getValue()));
        s.put("Biome Blend", String.valueOf(o.getBiomeBlendRadius().getValue()));
        s.put("Mipmaps", String.valueOf(o.getMipmapLevels().getValue()));
        s.put("Menu Blur", String.valueOf(o.getMenuBackgroundBlurriness().getValue()));
        s.put("FPS Target", c.fpsTarget == 0 ? "Off" : String.valueOf(c.fpsTarget));
        s.put("Particle Limit", limit(c.particleLimit));
        s.put("Max Entities", limit(c.maxEntities));
        s.put("Particle Range", limit(c.particleRange));
        s.put("Entity Distance", c.entityDistance >= BoostConfig.ENTITY_DISTANCE_UNLIMITED ? "Unlimited" : c.entityDistance * 8 + "");
        s.put("Block Entity Distance", String.valueOf(c.blockEntityDistance * 8));
        s.put("Item Distance", limit(c.itemDistance));
        s.put("Simple Items", onOff(c.simpleItems));
        s.put("Simple Far Mobs", limit(c.farMobDetail));
        s.put("Disable Particles", onOff(c.disableParticles));
        s.put("Hide Weather", onOff(c.hideWeather));
        s.put("Animated Textures", onOff(c.animatedTextures));
        s.put("Smart Culling", onOff(c.smartCulling));
        s.put("Hide Sky", onOff(c.hideSky));
        s.put("Hide Beacon Beams", onOff(c.hideBeaconBeams));
        s.put("Hide Decorations", onOff(c.hideDecorations));
        s.put("Hide Name Tags", onOff(c.hideNameTags));
        s.put("Hide Vignette", onOff(c.hideVignette));
        return s;
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private static String limit(int value) {
        return value < 0 ? "Off" : String.valueOf(value);
    }

    /** A finished benchmark, saved in mineboost.json so the next run can compare against it. */
    public static final class Result {
        public int avg;
        public int low;
        public String preset;
        public Map<String, String> settings;
    }
}
