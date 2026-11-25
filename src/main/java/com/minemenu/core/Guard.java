package com.minemenu.core;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Safe Mode: every hack runs inside {@link #run}. If it throws, the hack is switched off, the error goes to
 * logs/latest.log with the hack's name, a toast says so, and the game carries on instead of crashing.
 */
public final class Guard {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineMenu");
    /** Errors per shared system (not a single hack), to stop toast spam: a system that keeps failing is muted. */
    private static final Map<String, Integer> systemErrors = new ConcurrentHashMap<>();

    private Guard() {
    }

    /** Runs a piece of one hack. On an exception: the hack is turned off. Returns false if it failed. */
    public static boolean run(Feature feature, Runnable body) {
        try {
            body.run();
            return true;
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vm) {
                throw vm; // out of memory etc.: nothing sensible to do here
            }
            LOGGER.error("[Safe Mode] {} was disabled because of an error", feature.displayName(), t);
            if (CheatState.isOn(feature)) {
                CheatState.toggle(feature);
            }
            toast(Tr.format("minemenu.safe_mode.disabled", "%s was disabled because of an error", feature.displayName()), Tr.get("minemenu.safe_mode.details", "Details are in logs/latest.log"));
            return false;
        }
    }

    /** Runs a piece of shared code (not one hack). Logged and shown once; after 3 errors it's only logged. */
    public static boolean run(String system, Runnable body) {
        try {
            body.run();
            return true;
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vm) {
                throw vm;
            }
            int count = systemErrors.merge(system, 1, Integer::sum);
            LOGGER.error("[Safe Mode] error in {} (#{})", system, count, t);
            if (count <= 3) {
                toast(Tr.format("minemenu.safe_mode.caught", "MineMenu: an error in %s was caught", system), Tr.get("minemenu.safe_mode.keeps_running", "The game keeps running. Details in logs/latest.log"));
            }
            return false;
        }
    }

    /** A small notification in the top-right corner. Safe to call from any thread. */
    public static void toast(String title, String description) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.literal(title), description == null ? null : Text.literal(description)));
    }
}
