package com.minexray.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/**
 * Sodium replaces Minecraft's chunk builder, which Classic X-Ray changes to hide blocks. Its internals change
 * between versions, so rather than patching them (and risking broken chunks or crashes), Classic X-Ray is simply
 * unavailable with Sodium and says so; Ore ESP, which doesn't touch the chunk builder, works everywhere.
 */
public final class SodiumCompat {
    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("sodium")
            || FabricLoader.getInstance().isModLoaded("embeddium");

    private SodiumCompat() {
    }

    public static boolean present() {
        return PRESENT;
    }

    public static void logChoice() {
        if (PRESENT) {
            LoggerFactory.getLogger("MineXray").info("[MineXray] Sodium found: Classic X-Ray is off (use Ore ESP), its mixins weren't applied");
        }
    }
}
