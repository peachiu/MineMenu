package com.mineboost;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class MineBoostClient implements ClientModInitializer {
    private static KeyBinding openKey;
    private static KeyBinding batteryKey;
    private static int ticks;

    @Override
    public void onInitializeClient() {
        BoostConfig.load();
        com.mineboost.renderscale.Sharpen.register();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mineboost.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, "key.categories.mineboost"));

        batteryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mineboost.battery_saver", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.mineboost"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BoostGuard.run("Dynamic Render Scale", () -> com.mineboost.renderscale.RenderScale.tick(client),
                    () -> BoostConfig.get().dynamicRenderScale = false);
            BoostGuard.run("Context rules", () -> com.mineboost.tools.ContextRules.tick(client), () -> BoostConfig.get().contextRules = false);
            BoostGuard.run("Impostors", () -> Impostors.tick(client), () -> BoostConfig.get().impostorDistance = 0);
            if (++ticks % 10 == 0) {
                com.mineboost.doctor.MemoryScreen.sample();
            }
            while (batteryKey.wasPressed()) {
                BoostConfig.get().batterySaver = !BoostConfig.get().batterySaver;
                BoostConfig.save();
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.translatable("mineboost.text.battery_saver_s", net.minecraft.text.Text.translatable(BoostConfig.get().batterySaver ? "mineboost.text.on" : "mineboost.text.off"))
                            .formatted(net.minecraft.util.Formatting.GREEN), true);
                }
            }
        });
        // Leaving a world: free MineBoost's caches and the Render Scale framebuffer, so nothing piles up.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Culling.clear();
            EntityBudget.clear();
            SectionCulling.clear();
            com.mineboost.renderscale.RenderScale.release();
            Impostors.clear();
            com.mineboost.tools.BoostAB.cancel(client);
        });
        ClientTickEvents.END_CLIENT_TICK.register(SmartRenderDistance::tick);
        ClientTickEvents.END_CLIENT_TICK.register(EntityBudget::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new BoostScreen(null, client.options));
                }
            }
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            FpsOverlay.render(context);
            Benchmark.onFrame(context);
            AutoPreset.onFrame(context);
            BoostGuard.run("A/B test", () -> com.mineboost.tools.BoostAB.onFrame(context), () -> { });
            com.mineboost.doctor.LagDoctor.renderHud(context);
            BoostGuard.run("Spike log", () -> com.mineboost.doctor.SpikeDetector.onFrame(net.minecraft.client.MinecraftClient.getInstance()),
                    () -> BoostConfig.get().spikeLog = false);
        });
    }
}
