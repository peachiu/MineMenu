package com.mineboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MineBoost's Safe Mode: a boost that throws is switched off (its {@code disable} runs), the error goes to
 * logs/latest.log with the boost's name, a toast says so, and the game keeps running.
 */
public final class BoostGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineBoost");

    private BoostGuard() {
    }

    public static boolean run(String name, Runnable body, Runnable disable) {
        try {
            body.run();
            return true;
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vm) {
                throw vm;
            }
            fail(name, t, disable);
            return false;
        }
    }

    public static void fail(String name, Throwable t, Runnable disable) {
        LOGGER.error("[Safe Mode] {} was disabled because of an error", name, t);
        try {
            disable.run();
            BoostConfig.save();
        } catch (Throwable ignored) {
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.translatable("mineboost.text.s_was_disabled_because_of_an", name), Text.translatable("mineboost.text.details_are_in_logs_latest_log")));
        }
    }
}
