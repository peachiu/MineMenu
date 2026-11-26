package com.minexray;

import com.minexray.compat.SodiumCompat;
import com.minexray.screen.XrayScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/**
 * MineXray's keys (Options > Controls > Key Binds > MineXray): Classic X-Ray on X, Ore ESP on G, the other modes
 * and tools unbound until you set them.
 */
public final class XrayKeys {
    public static final String CATEGORY = "key.categories.minexray";
    private static final Map<XrayMode, KeyBinding> modeKeys = new EnumMap<>(XrayMode.class);
    public static KeyBinding nextPreset;
    public static KeyBinding scanNow;
    public static KeyBinding openTab;
    public static KeyBinding addLookedAt;

    private XrayKeys() {
    }

    static void register() {
        for (XrayMode mode : XrayMode.values()) {
            modeKeys.put(mode, key("key.minexray." + mode.id, mode.defaultKey));
        }
        nextPreset = key("key.minexray.next_preset", -1);
        scanNow = key("key.minexray.scan_now", -1);
        openTab = key("key.minexray.open", -1);
        addLookedAt = key("key.minexray.add_looked_at", -1);
    }

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, code < 0 ? GLFW.GLFW_KEY_UNKNOWN : code, CATEGORY));
    }

    public static KeyBinding of(XrayMode mode) {
        return modeKeys.get(mode);
    }

    static void tick(MinecraftClient client) {
        for (Map.Entry<XrayMode, KeyBinding> entry : modeKeys.entrySet()) {
            while (entry.getValue().wasPressed()) {
                toggle(client, entry.getKey());
            }
        }
        while (nextPreset.wasPressed()) {
            if (check(client)) {
                message(client, Text.translatable("minexray.preset.now", PresetManager.next()).formatted(Formatting.AQUA));
            }
        }
        while (scanNow.wasPressed()) {
            if (check(client)) {
                client.setScreen(new XrayScreen(null, XrayScreen.Page.SCANNER, true));
            }
        }
        while (openTab.wasPressed()) {
            client.setScreen(new XrayScreen(null, XrayScreen.Page.OVERVIEW, false));
        }
        while (addLookedAt.wasPressed()) {
            if (check(client)) {
                XrayActions.addLookedAt(client);
            }
        }
    }

    /** Switches a mode, with a short message above the hotbar. */
    public static void toggle(MinecraftClient client, XrayMode mode) {
        if (!check(client)) {
            return;
        }
        if (mode == XrayMode.CLASSIC && SodiumCompat.present()) {
            message(client, Text.translatable("minexray.classic.sodium").formatted(Formatting.YELLOW));
            return;
        }
        XrayConfig config = XrayConfig.get();
        boolean on = !config.isOn(mode);
        config.set(mode, on);
        message(client, Text.literal(mode.displayName() + ": ").append(Text.translatable(on ? "minexray.on" : "minexray.off")
                .formatted(on ? Formatting.GREEN : Formatting.RED)));
    }

    /** MineXray only works in singleplayer: says so and returns false on a server. */
    static boolean check(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        if (!MineXrayClient.allowed()) {
            message(client, Text.translatable("minexray.singleplayer_only").formatted(Formatting.RED));
            return false;
        }
        return true;
    }

    static void message(MinecraftClient client, Text text) {
        if (client.player != null) {
            client.player.sendMessage(text, true);
        }
    }
}
