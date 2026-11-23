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

    @Override
    public void onInitializeClient() {
        BoostConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mineboost.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, "key.categories.mineboost"));

        ClientTickEvents.END_CLIENT_TICK.register(SmartRenderDistance::tick);
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
        });
    }
}
