package com.minemeboost.clienttest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Entry point of the in-game test mod (src/clienttest). Only loaded by the runClientTests Gradle task, which puts this
 * jar next to the real MineMeBoost jar; it is never part of a release. The suite comes from
 * -Dminemeboost.clienttest.suite (full, soak, ab, mp, iris).
 */
public class ClientTestMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        String suite = System.getProperty("minemeboost.clienttest.suite", "full");
        Harness harness = switch (suite) {
            case "soak" -> new SoakSuite(Integer.getInteger("minemeboost.clienttest.minutes", 15));
            case "ab" -> new AbSuite();
            case "mp" -> new MpSuite();
            case "iris" -> new IrisSuite();
            default -> new FullSuite();
        };
        harness.build();
        ClientTickEvents.END_CLIENT_TICK.register(harness::tick);
        HudRenderCallback.EVENT.register((context, tickCounter) -> harness.onFrame());
    }
}
