package com.minemenu.core;

import com.minemenu.hud.Macros;
import com.minemenu.ui.CommandPalette;
import com.minemenu.ui.MenuEntries;
import com.minemenu.ui.MenuEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A key for any hack, action or macro (set by right-clicking its button), plus Ctrl+K for the Command Palette.
 * Keys are read straight from the keyboard each tick (only with no menu open), so no restart is needed.
 */
public final class Keys {
    private static final Map<Integer, Boolean> wasDown = new HashMap<>();

    private Keys() {
    }

    private static boolean down(MinecraftClient client, int key) {
        return key > 0 && InputUtil.isKeyPressed(client.getWindow().getHandle(), key);
    }

    /** True the tick a key goes down. */
    private static boolean pressed(MinecraftClient client, int key) {
        boolean now = down(client, key);
        Boolean before = wasDown.put(key, now);
        return now && (before == null || !before);
    }

    public static void tick(MinecraftClient client) {
        Macros.tick(client);
        if (client.currentScreen != null || client.player == null) {
            wasDown.replaceAll((k, v) -> true); // don't fire for keys already held when the menu closes
            return;
        }
        boolean ctrl = down(client, GLFW.GLFW_KEY_LEFT_CONTROL) || down(client, GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (pressed(client, GLFW.GLFW_KEY_K) && ctrl) {
            client.setScreen(new CommandPalette(null));
            return;
        }
        if (ctrl) {
            return; // Ctrl+key combinations belong to other things
        }
        for (Map.Entry<String, Integer> binding : new ArrayList<>(CheatConfig.get().keys.entrySet())) {
            if (pressed(client, binding.getValue())) {
                MenuEntry entry = MenuEntries.byId(binding.getKey());
                if (entry != null) {
                    MenuEntries.run(client, null, entry);
                }
            }
        }
        for (CheatConfig.Macro macro : CheatConfig.get().macros) {
            if (pressed(client, macro.key)) {
                Macros.run(macro);
            }
        }
    }

    /** Everything else already on {@code key}: Minecraft's own keys, other hacks and macros. */
    public static List<String> conflicts(MinecraftClient client, int key, String exceptId) {
        List<String> list = new ArrayList<>();
        if (key <= 0) {
            return list;
        }
        for (KeyBinding binding : client.options.allKeys) {
            if (!binding.isUnbound() && InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey()).getCode() == key) {
                list.add(net.minecraft.text.Text.translatable(binding.getTranslationKey()).getString());
            }
        }
        for (Map.Entry<String, Integer> binding : CheatConfig.get().keys.entrySet()) {
            if (binding.getValue() == key && !binding.getKey().equals(exceptId)) {
                MenuEntry entry = MenuEntries.byId(binding.getKey());
                list.add(entry != null ? entry.name() : binding.getKey());
            }
        }
        for (CheatConfig.Macro macro : CheatConfig.get().macros) {
            if (macro.key == key && !("macro:" + macro.name).equals(exceptId)) {
                list.add(Tr.format("minemenu.entry.macro", "Macro: %s", macro.name));
            }
        }
        return list;
    }

    public static String name(int key) {
        return key <= 0 ? Tr.get("minemenu.key.none", "None") : InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
    }
}
