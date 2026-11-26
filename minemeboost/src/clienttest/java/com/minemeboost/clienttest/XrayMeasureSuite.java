package com.minemeboost.clienttest;

import com.minemenu.Feature;
import com.minexray.XrayConfig;
import com.minexray.XrayMode;
import net.minecraft.world.GameMode;

/**
 * MineXray's cost per mode, measured underground in a normal world (spectator, looking along a cave level):
 * each mode off/on three times, after it has had time to scan (and, for Classic, to rebuild the chunks).
 */
class XrayMeasureSuite extends Harness {
    XrayMeasureSuite() {
        super("xraym");
    }

    @Override
    protected void build() {
        step(200, c -> {
            run(() -> {
                host().changeGameMode(GameMode.SPECTATOR);
                server.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            });
            for (XrayMode mode : XrayMode.values()) XrayConfig.get().set(mode, false);
            XrayConfig.get().adaptiveRange = false;
            c.options.getViewDistance().setValue(8);
            run(() -> host().requestTeleport(host().getX(), -20, host().getZ()));
        });
        step(400, c -> { c.player.setYaw(0); c.player.setPitch(10); log.add("y " + Math.round(c.player.getY()) + ", view distance 8"); shot(c, "scene.png"); });
        measure("Ore ESP 32", 200, on -> { XrayConfig.get().oreRadius = 32; XrayConfig.get().set(XrayMode.ORE_ESP, on); });
        measure("Ore ESP 64", 300, on -> { XrayConfig.get().oreRadius = 64; XrayConfig.get().set(XrayMode.ORE_ESP, on); });
        measure("Ore ESP 128", 500, on -> { XrayConfig.get().oreRadius = 128; XrayConfig.get().set(XrayMode.ORE_ESP, on); });
        step(5, c -> XrayConfig.get().oreRadius = 32);
        measure("Block ESP", 200, on -> XrayConfig.get().set(XrayMode.BLOCK_ESP, on));
        measure("Cave Finder", 300, on -> XrayConfig.get().set(XrayMode.CAVE_FINDER, on));
        measure("Fluid View", 200, on -> XrayConfig.get().set(XrayMode.FLUID_VIEW, on));
        measure("Classic X-Ray", 600, on -> XrayConfig.get().set(XrayMode.CLASSIC, on));
        step(5, c -> { on(Feature.MOB_GLOW); });
        step(5, c -> check("measurements done", true));
    }

    private void measure(String name, int settle, java.util.function.Consumer<Boolean> set) {
        for (int r = 0; r < 3; r++) {
            step(settle, c -> set.accept(false));
            step(120, c -> startRec());
            step(5, c -> { stopRec(name + " OFF"); set.accept(true); });
            step(settle, c -> {});
            step(120, c -> startRec());
            step(5, c -> stopRec(name + " ON"));
        }
        step(5, c -> {
            set.accept(false);
            int k = stats.size();
            double off = 0, on = 0, offLow = 0, onLow = 0;
            for (int i = k - 6; i < k; i += 2) { off += stats.get(i)[0]; offLow += stats.get(i)[1]; on += stats.get(i + 1)[0]; onLow += stats.get(i + 1)[1]; }
            log.add(String.format("RESULT %s: avg %.1f -> %.1f (%+.0f%%), 1%% low %.1f -> %.1f (%+.0f%%)", name, off / 3, on / 3, (on - off) * 100 / off,
                    offLow / 3, onLow / 3, (onLow - offLow) * 100 / offLow));
        });
    }
}
