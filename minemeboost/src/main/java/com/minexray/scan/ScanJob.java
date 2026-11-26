package com.minexray.scan;

import com.minexray.MineXrayClient;

import java.util.List;

/**
 * "Scan now": scans every section within the radius (even with the ESP modes off), waits until the background
 * thread is done, and keeps the list of veins. Runs on whether or not the scanner page stays open.
 */
public final class ScanJob {
    private static List<VeinMerger.Vein> results = List.of();
    private static boolean running;
    private static int idleTicks;
    private static long started;
    private static Runnable onDone;

    private ScanJob() {
    }

    public static void start(int radius, Runnable whenDone) {
        running = true;
        idleTicks = 0;
        started = System.currentTimeMillis();
        onDone = whenDone;
        MineXrayClient.forceRadius(Math.max(16, Math.min(128, radius)));
    }

    public static boolean running() {
        return running;
    }

    public static List<VeinMerger.Vein> results() {
        return results;
    }

    /** Client tick. */
    public static void tick() {
        if (!running) {
            return;
        }
        idleTicks = SectionScanner.idle() ? idleTicks + 1 : 0;
        if (idleTicks >= 10 || System.currentTimeMillis() - started > 90_000) {
            results = VeinMerger.veins();
            running = false;
            MineXrayClient.forceRadius(0);
            if (onDone != null) {
                onDone.run();
                onDone = null;
            }
        }
    }

    public static void cancel() {
        running = false;
        onDone = null;
        MineXrayClient.forceRadius(0);
    }
}
