package com.minemenu.hud;

import com.minemenu.MineMenuClient;
import com.minemenu.ServerCheats;
import com.minemenu.core.CheatConfig;
import com.minemenu.core.Guard;
import com.minemenu.ui.MenuEntries;
import com.minemenu.ui.MenuEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Macros: a list of steps on one key. Steps are "feature:FLY" (switch a hack), "action:DAY" (run an action),
 * "screen:..." / "waypoint:N" (anything from the menu), "cmd:time set day" (a command, run as the host with
 * cheats allowed) and "wait:20" (ticks). Record one by pressing Record, then using the menu as normal.
 */
public final class Macros {
    private static final Deque<String> queue = new ArrayDeque<>();
    private static int waitTicks;
    private static List<String> recording;

    private Macros() {
    }

    public static void run(CheatConfig.Macro macro) {
        queue.addAll(macro.steps);
    }

    public static boolean isRecording() {
        return recording != null;
    }

    public static void startRecording() {
        recording = new ArrayList<>();
    }

    /** Stops recording and returns the steps. */
    public static List<String> stopRecording() {
        List<String> steps = recording == null ? List.of() : recording;
        recording = null;
        return steps;
    }

    /** Called whenever an entry is used from the menu, a key or the palette. */
    public static void record(String id) {
        if (recording != null && !id.startsWith("macro:")) {
            recording.add(id);
        }
    }

    public static void tick(MinecraftClient client) {
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        while (!queue.isEmpty() && waitTicks == 0) {
            String step = queue.poll().trim();
            Guard.run("macro step " + step, () -> runStep(client, step));
        }
    }

    private static void runStep(MinecraftClient client, String step) {
        if (step.startsWith("wait:")) {
            waitTicks = Math.max(0, Integer.parseInt(step.substring(5).trim()));
            return;
        }
        if (!MineMenuClient.requireSingleplayer(client)) {
            queue.clear();
            return;
        }
        if (step.startsWith("cmd:")) {
            String command = step.substring(4).trim();
            String cmd = command.startsWith("/") ? command.substring(1) : command;
            IntegratedServer server = client.getServer();
            if (server != null) {
                server.execute(() -> {
                    var player = ServerCheats.host(server);
                    if (player != null) {
                        server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4), cmd);
                    }
                });
            }
            return;
        }
        MenuEntry entry = MenuEntries.byId(step);
        if (entry == null) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Macro step not found: " + step).formatted(Formatting.RED), false);
            }
            return;
        }
        entry.run().accept(client, null);
    }
}
