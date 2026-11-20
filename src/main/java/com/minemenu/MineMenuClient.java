package com.minemenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class MineMenuClient implements ClientModInitializer {
    private static final String CATEGORY = "key.categories.minemenu";

    private static KeyBinding openMenuKey;
    private static KeyBinding toggleFlyKey;
    private static KeyBinding toggleGodKey;
    private static KeyBinding killHostileKey;

    @Override
    public void onInitializeClient() {
        openMenuKey = register("key.minemenu.open_menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        // Unbound by default; set them in Options > Controls > Key Binds > MineMenu.
        toggleFlyKey = register("key.minemenu.toggle_fly", GLFW.GLFW_KEY_UNKNOWN);
        toggleGodKey = register("key.minemenu.toggle_god", GLFW.GLFW_KEY_UNKNOWN);
        killHostileKey = register("key.minemenu.kill_hostile", GLFW.GLFW_KEY_UNKNOWN);

        ClientTickEvents.END_CLIENT_TICK.register(MineMenuClient::onClientTick);
        HudRenderCallback.EVENT.register((context, tickCounter) -> renderHud(context));

        // These fire for the integrated (singleplayer) server, which runs inside the client.
        ServerLifecycleEvents.SERVER_STARTED.register(ServerCheats::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerCheats::revertAll);
        ServerTickEvents.END_SERVER_TICK.register(ServerCheats::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                // God mode blocks everything except the void and /kill, so you can't get stuck.
                !ServerCheats.isProtectedHost(entity)
                        || source.isOf(DamageTypes.OUT_OF_WORLD)
                        || source.isOf(DamageTypes.GENERIC_KILL));
    }

    private static KeyBinding register(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, key, CATEGORY));
    }

    private static void onClientTick(MinecraftClient client) {
        while (openMenuKey.wasPressed()) {
            if (requireSingleplayer(client)) {
                client.setScreen(new MineMenuScreen());
            }
        }
        while (toggleFlyKey.wasPressed()) {
            if (requireSingleplayer(client)) {
                toggleWithMessage(client, Feature.FLY);
            }
        }
        while (toggleGodKey.wasPressed()) {
            if (requireSingleplayer(client)) {
                toggleWithMessage(client, Feature.GOD_MODE);
            }
        }
        while (killHostileKey.wasPressed()) {
            if (requireSingleplayer(client)) {
                runAction(client, ServerCheats.Action.KILL_HOSTILE);
            }
        }
    }

    /** True in a private singleplayer world; otherwise tells the player why nothing happened. */
    public static boolean requireSingleplayer(MinecraftClient client) {
        if (isSingleplayer(client)) {
            return true;
        }
        if (client.player != null) {
            client.player.sendMessage(Text.literal("MineMenu only works in singleplayer worlds (not multiplayer or LAN).")
                    .formatted(Formatting.RED), true);
        }
        return false;
    }

    public static boolean isSingleplayer(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        return client.player != null && client.isIntegratedServerRunning() && server != null && !server.isRemote();
    }

    public static void toggleWithMessage(MinecraftClient client, Feature feature) {
        boolean on = CheatState.toggle(feature);
        if (client.player != null) {
            client.player.sendMessage(Text.literal(feature.displayName() + ": ")
                    .append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED)), true);
        }
    }

    /** Runs a one-shot action on the integrated server thread. */
    public static void runAction(MinecraftClient client, ServerCheats.Action action) {
        IntegratedServer server = client.getServer();
        if (server != null) {
            server.execute(() -> ServerCheats.run(server, action));
        }
    }

    private static void renderHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!CheatState.hudVisible() || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()
                || !isSingleplayer(client)) {
            return;
        }
        List<Feature> enabled = CheatState.enabled();
        CheatState.Size size = CheatState.size();
        if (enabled.isEmpty() && size == CheatState.Size.NORMAL) {
            return;
        }
        TextRenderer font = client.textRenderer;
        int x = 4;
        int y = 4;
        context.drawTextWithShadow(font, Text.literal("MineMenu").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), x, y, 0xFFFFFF);
        y += 11;
        for (Feature feature : enabled) {
            context.drawTextWithShadow(font, Text.literal("• " + feature.displayName()).formatted(Formatting.GREEN), x, y, 0xFFFFFF);
            y += 10;
        }
        if (size != CheatState.Size.NORMAL) {
            context.drawTextWithShadow(font, Text.literal("• Size: " + size.displayName()).formatted(Formatting.AQUA), x, y, 0xFFFFFF);
        }
    }
}
