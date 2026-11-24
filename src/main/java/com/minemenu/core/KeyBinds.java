package com.minemenu.core;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** The 3.4 keys that belong to a hack (changeable in Options > Controls > Key Binds > MineMenu). */
public final class KeyBinds {
    public static KeyBinding dash;
    public static KeyBinding morphPower;
    public static KeyBinding unmorph;
    public static KeyBinding mindControl;
    public static KeyBinding bigMap;
    public static KeyBinding batterySaver;

    private KeyBinds() {
    }

    public static void register() {
        dash = key("key.minemenu.dash", GLFW.GLFW_KEY_V);
        morphPower = key("key.minemenu.morph_power", GLFW.GLFW_KEY_R);
        unmorph = key("key.minemenu.unmorph", GLFW.GLFW_KEY_U);
        mindControl = key("key.minemenu.mind_control", GLFW.GLFW_KEY_N);
        bigMap = key("key.minemenu.big_map", GLFW.GLFW_KEY_M);
    }

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, code, "key.categories.minemenu"));
    }
}
