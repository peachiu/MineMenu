package com.minemenu.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;

import java.util.EnumSet;
import java.util.Set;

/**
 * The one owner of the doDaylightCycle game rule. Always Day and a stopped Day Speed both want the clock
 * stopped; before 3.4 each remembered its own "original" value, so switching them on and off in some orders
 * left the clock stopped while the slider said Normal. Now: stopped while any reason is active, and the
 * original value comes back when none is.
 */
public final class DayCycle {
    public enum Reason { ALWAYS_DAY, DAY_SPEED_STOPPED }

    private static final Set<Reason> reasons = EnumSet.noneOf(Reason.class);
    private static Boolean original;

    private DayCycle() {
    }

    public static synchronized void reset() {
        reasons.clear();
        original = null;
    }

    public static synchronized void stop(MinecraftServer server, Reason reason) {
        GameRules.BooleanRule rule = server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE);
        if (reasons.isEmpty()) {
            original = rule.get();
        }
        reasons.add(reason);
        rule.set(false, server);
    }

    public static synchronized void release(MinecraftServer server, Reason reason) {
        if (!reasons.remove(reason) || !reasons.isEmpty()) {
            return;
        }
        server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(original == null || original, server);
        original = null;
    }
}
