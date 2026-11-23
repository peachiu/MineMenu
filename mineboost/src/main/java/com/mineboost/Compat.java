package com.mineboost;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Other performance mods MineBoost knows about. When one of them already does something MineBoost
 * does, MineBoost turns its own version off (instead of doing the work twice or fighting over it)
 * and the MineBoost menu says so.
 */
public final class Compat {
    public static final boolean SODIUM = loaded("sodium");
    public static final boolean LITHIUM = loaded("lithium");
    public static final boolean FERRITECORE = loaded("ferritecore");
    public static final boolean IMMEDIATELYFAST = loaded("immediatelyfast");
    public static final boolean ENTITYCULLING = loaded("entityculling");

    private Compat() {
    }

    static boolean loaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    /** Smart Culling is skipped when EntityCulling is installed: it does the same thing (on another thread). */
    public static boolean smartCulling() {
        return BoostConfig.get().smartCulling && !ENTITYCULLING;
    }

    /** The detected mods, e.g. "Sodium, Lithium". Empty if none. */
    public static String detected() {
        List<String> names = new ArrayList<>();
        if (SODIUM) names.add("Sodium");
        if (LITHIUM) names.add("Lithium");
        if (FERRITECORE) names.add("FerriteCore");
        if (IMMEDIATELYFAST) names.add("ImmediatelyFast");
        if (ENTITYCULLING) names.add("EntityCulling");
        return String.join(", ", names);
    }

    /** What MineBoost turned off because of the detected mods, one line each. */
    public static List<String> turnedOff() {
        List<String> lines = new ArrayList<>();
        if (ENTITYCULLING) {
            lines.add("Smart Culling: EntityCulling already hides things behind walls.");
        }
        if (SODIUM) {
            lines.add("Chunk Builder (vanilla setting): Sodium has its own chunk renderer and ignores it.");
        }
        return lines;
    }
}
