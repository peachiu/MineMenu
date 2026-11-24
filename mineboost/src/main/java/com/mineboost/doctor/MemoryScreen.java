package com.mineboost.doctor;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Memory panel: RAM used / allowed, a graph of the last minute, garbage collection counts, and the JVM Advisor:
 * how much RAM to give Minecraft on this PC and a line of Java arguments to copy into your launcher. MineBoost
 * never changes launcher files itself.
 */
public class MemoryScreen extends Screen {
    private static final int SAMPLES = 120;
    private static final long[] used = new long[SAMPLES];
    private static int next;
    private static int count;
    private final Screen parent;
    private String message = "";

    public MemoryScreen(Screen parent) {
        super(Text.literal("Memory"));
        this.parent = parent;
    }

    /** Called every 10 client ticks (half a second) by MineBoostClient. */
    public static void sample() {
        Runtime runtime = Runtime.getRuntime();
        used[next] = runtime.totalMemory() - runtime.freeMemory();
        next = (next + 1) % SAMPLES;
        count = Math.min(SAMPLES, count + 1);
    }

    /** Physical RAM of the PC in bytes, or 0 if Java can't tell. */
    static long physicalMemory() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            return os.getTotalMemorySize();
        }
        return 0;
    }

    /** Recommended -Xmx in GB. Integrated graphics use part of the RAM as video memory, so leave plenty free. */
    static int recommendedGb(long physical) {
        double gb = physical / 1024.0 / 1024 / 1024;
        if (gb <= 0) return 3;
        if (gb <= 4.5) return 2;
        if (gb <= 8.5) return 3;
        if (gb <= 16.5) return 4;
        return 6;
    }

    static String jvmLine(int gb) {
        return "-Xms" + gb + "G -Xmx" + gb + "G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 "
                + "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 "
                + "-XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 "
                + "-XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 "
                + "-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1";
    }

    @Override
    protected void init() {
        int gb = recommendedGb(physicalMemory());
        addDrawableChild(ButtonWidget.builder(Text.literal("Copy JVM arguments (" + gb + " GB)"), b -> {
            this.client.keyboard.setClipboard(jvmLine(gb));
            message = "Copied. Paste it into your launcher: Installations > Edit > More options > JVM arguments.";
        }).dimensions(this.width / 2 - 154, this.height - 26, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(this.width / 2 + 50, this.height - 26, 104, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long now = runtime.totalMemory() - runtime.freeMemory();
        int left = this.width / 2 - 160;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Memory & JVM Advisor").formatted(Formatting.GREEN, Formatting.BOLD), this.width / 2, 8, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal(String.format("RAM used: %d / %d MB (%d%%)", now >> 20, max >> 20, now * 100 / max)), left, 24, 0xFFFFFF);
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, bean.getCollectionCount());
            gcTime += Math.max(0, bean.getCollectionTime());
        }
        context.drawTextWithShadow(this.textRenderer, Text.literal(String.format("Garbage collections: %d (%.1f s in total)", gcCount, gcTime / 1000.0))
                .formatted(Formatting.GRAY), left, 35, 0xFFFFFF);
        // Graph of the last minute.
        int graphTop = 48;
        int graphHeight = 50;
        context.fill(left, graphTop, left + 320, graphTop + graphHeight, 0x80000000);
        for (int i = 0; i < count; i++) {
            int index = Math.floorMod(next - count + i, SAMPLES);
            int h = (int) (used[index] * graphHeight / Math.max(1, max));
            int x = left + 320 - (count - i) * 320 / SAMPLES;
            context.fill(x, graphTop + graphHeight - h, x + Math.max(1, 320 / SAMPLES), graphTop + graphHeight, 0xC055FF55);
        }
        long physical = physicalMemory();
        int gb = recommendedGb(physical);
        int y = graphTop + graphHeight + 10;
        List<String> advice = List.of(
                physical > 0 ? String.format("This PC has %.1f GB of RAM. Minecraft may use up to %.1f GB now.", physical / 1073741824.0, max / 1073741824.0)
                        : String.format("Minecraft may use up to %.1f GB now.", max / 1073741824.0),
                "Recommended: " + gb + " GB. More isn't faster: Java then cleans up less often but for longer (stutters),",
                "and with integrated graphics the rest of the RAM is also your video memory.",
                max >> 30 < gb - 0.5 ? "You give it less than recommended: copy the line below into your launcher."
                        : max >> 30 > gb + 1 ? "You give it more than needed: lowering it can reduce stutters." : "Your setting is fine.");
        for (String line : advice) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(line).formatted(Formatting.GRAY), left, y, 0xFFFFFF);
            y += 11;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(message).formatted(Formatting.GREEN), this.width / 2, this.height - 38, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
