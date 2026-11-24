package com.mineboost.doctor;

import com.mineboost.BoostConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Spike detector: every frame slower than 50 ms is noted with what was going on at that moment (garbage
 * collection, chunks waiting to be built, entities and particles drawn, the singleplayer server's tick), on the
 * Lag Doctor screen and in config/minemeboost/spikes.log (written every few seconds, not during the spike).
 */
public final class SpikeDetector {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("minemeboost").resolve("spikes.log");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Deque<String> recent = new ArrayDeque<>();
    private static final List<String> unsaved = new ArrayList<>();
    private static long lastFrame;
    private static long lastFlush;
    private static long lastGcCount;

    private SpikeDetector() {
    }

    public static List<String> recent() {
        return new ArrayList<>(recent);
    }

    /** Called once per frame. */
    public static void onFrame(MinecraftClient client) {
        long now = System.nanoTime();
        long frame = lastFrame == 0 ? 0 : now - lastFrame;
        lastFrame = now;
        long gc = gcCount();
        boolean gcHappened = gc != lastGcCount;
        lastGcCount = gc;
        if (!BoostConfig.get().spikeLog || client.world == null || frame < 50_000_000L || frame > 5_000_000_000L
                || client.currentScreen != null) {
            flushIfDue(now);
            return;
        }
        StringBuilder what = new StringBuilder();
        what.append(LocalTime.now().format(TIME)).append(String.format("  %.0f ms", frame / 1e6));
        if (gcHappened) {
            what.append(" | garbage collection");
        }
        what.append(" | ").append(client.worldRenderer.getChunksDebugString().replace("Chunks: ", "chunks "));
        what.append(" | ").append(client.worldRenderer.getEntitiesDebugString());
        what.append(" | particles ").append(client.particleManager.getDebugString().trim());
        IntegratedServer server = client.getServer();
        if (server != null) {
            what.append(String.format(" | server tick %.0f ms", server.getAverageNanosPerTick() / 1e6));
        }
        String line = what.toString();
        recent.addFirst(line);
        while (recent.size() > 30) {
            recent.removeLast();
        }
        unsaved.add(line);
        flushIfDue(now);
    }

    private static long gcCount() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, bean.getCollectionCount());
        }
        return total;
    }

    private static void flushIfDue(long now) {
        if (unsaved.isEmpty() || now - lastFlush < 5_000_000_000L) {
            return;
        }
        lastFlush = now;
        List<String> lines = new ArrayList<>(unsaved);
        unsaved.clear();
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.exists(FILE) && Files.size(FILE) > 1_000_000) {
                Files.delete(FILE); // keep it small
            }
            Files.write(FILE, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
