package com.minexray;

import com.mineboost.tools.BoostAB;
import com.minexray.compat.SodiumCompat;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures what each MineXray mode costs with MineBoost's A/B tool: every test runs 4 s with all modes off and
 * 4 s with one mode on, twice. Results go to config/minemeboost/xray-cost-results.md (negative = FPS lost).
 * Your modes and radius are put back afterwards.
 */
public final class XrayCostTest {
    private XrayCostTest() {
    }

    public static void start(MinecraftClient client) {
        if (BoostAB.running()) {
            return;
        }
        XrayConfig config = XrayConfig.get();
        boolean[] saved = new boolean[XrayMode.values().length];
        for (XrayMode mode : XrayMode.values()) {
            saved[mode.ordinal()] = config.isOn(mode);
        }
        int savedRadius = config.oreRadius;
        boolean savedAdaptive = config.adaptiveRange;
        config.adaptiveRange = false; // measure the radius asked for, not a shrunk one
        BoostAB.start(client, tests(), "xray-cost-results.md", () -> {
            for (XrayMode mode : XrayMode.values()) {
                config.set(mode, saved[mode.ordinal()]);
            }
            config.oreRadius = savedRadius;
            config.adaptiveRange = savedAdaptive;
            XrayConfig.markDirty();
        });
    }

    static List<BoostAB.Test> tests() {
        List<BoostAB.Test> list = new ArrayList<>();
        for (int radius : new int[]{32, 64, 128}) {
            list.add(new BoostAB.Test("Ore ESP " + radius, c -> only(XrayMode.ORE_ESP, radius), c -> only(null, 0)));
        }
        if (!SodiumCompat.present()) {
            list.add(new BoostAB.Test("Classic X-Ray", c -> only(XrayMode.CLASSIC, 0), c -> only(null, 0)));
        }
        list.add(new BoostAB.Test("Cave Finder", c -> only(XrayMode.CAVE_FINDER, 0), c -> only(null, 0)));
        list.add(new BoostAB.Test("Block ESP", c -> only(XrayMode.BLOCK_ESP, 0), c -> only(null, 0)));
        list.add(new BoostAB.Test("Entity ESP", c -> only(XrayMode.ENTITY_ESP, 0), c -> only(null, 0)));
        return list;
    }

    /** Exactly one mode on (null = none); {@code radius} > 0 also sets Ore ESP's radius. */
    private static void only(XrayMode on, int radius) {
        XrayConfig config = XrayConfig.get();
        for (XrayMode mode : XrayMode.values()) {
            config.set(mode, mode == on);
        }
        if (radius > 0) {
            config.oreRadius = radius;
        }
    }
}
