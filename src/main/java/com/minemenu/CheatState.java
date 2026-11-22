package com.minemenu;

import net.minecraft.util.Identifier;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Shared on/off state. Written by the client (menu, keybinds) and read by the
 * integrated server thread, so every access is synchronized.
 */
public final class CheatState {
    public enum Size {
        NORMAL("Normal", 0.0),
        GIANT("Giant", 3.0),   // scale x4
        TINY("Tiny", -0.75);   // scale x0.25

        private final String displayName;
        /** Value for an ADD_MULTIPLIED_BASE modifier on the scale attribute. */
        private final double scaleModifier;

        Size(String displayName, double scaleModifier) {
            this.displayName = displayName;
            this.scaleModifier = scaleModifier;
        }

        public String displayName() {
            return displayName;
        }

        public double scaleModifier() {
            return scaleModifier;
        }

        public Size next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final Set<Feature> ENABLED = EnumSet.noneOf(Feature.class);
    private static Size size = Size.NORMAL;
    private static boolean hudVisible = true;
    private static boolean fpsVisible = false;
    private static Identifier selectedMob = Identifier.ofVanilla("zombie");
    private static int spawnAmount = 1;
    private static final int[] SPAWN_AMOUNTS = {1, 5, 10};
    private static Identifier selectedItem = Identifier.ofVanilla("diamond");
    private static int giveAmount = 64;
    private static final int[] GIVE_AMOUNTS = {1, 16, 32, 64};
    private static boolean giveToGround = false;

    private CheatState() {
    }

    public static synchronized boolean isOn(Feature feature) {
        return ENABLED.contains(feature);
    }

    public static synchronized boolean toggle(Feature feature) {
        if (!ENABLED.remove(feature)) {
            ENABLED.add(feature);
            return true;
        }
        return false;
    }

    public static synchronized List<Feature> enabled() {
        return List.copyOf(ENABLED);
    }

    public static synchronized Size size() {
        return size;
    }

    public static synchronized Size cycleSize() {
        size = size.next();
        return size;
    }

    public static synchronized boolean hudVisible() {
        return hudVisible;
    }

    public static synchronized void toggleHud() {
        hudVisible = !hudVisible;
    }

    public static synchronized boolean fpsVisible() {
        return fpsVisible;
    }

    public static synchronized void toggleFps() {
        fpsVisible = !fpsVisible;
    }

    /** The mob the "Spawn Mob" picker and keybind will spawn. */
    public static synchronized Identifier selectedMob() {
        return selectedMob;
    }

    public static synchronized void setSelectedMob(Identifier id) {
        selectedMob = id;
    }

    public static synchronized int spawnAmount() {
        return spawnAmount;
    }

    /** Cycles 1 -> 5 -> 10 -> 1. */
    public static synchronized int cycleSpawnAmount() {
        for (int i = 0; i < SPAWN_AMOUNTS.length; i++) {
            if (SPAWN_AMOUNTS[i] == spawnAmount) {
                spawnAmount = SPAWN_AMOUNTS[(i + 1) % SPAWN_AMOUNTS.length];
                return spawnAmount;
            }
        }
        spawnAmount = 1;
        return spawnAmount;
    }

    /** The item the "Give Item" picker gives. */
    public static synchronized Identifier selectedItem() {
        return selectedItem;
    }

    public static synchronized void setSelectedItem(Identifier id) {
        selectedItem = id;
    }

    public static synchronized int giveAmount() {
        return giveAmount;
    }

    /** Cycles 1 -> 16 -> 32 -> 64 -> 1. */
    public static synchronized int cycleGiveAmount() {
        for (int i = 0; i < GIVE_AMOUNTS.length; i++) {
            if (GIVE_AMOUNTS[i] == giveAmount) {
                giveAmount = GIVE_AMOUNTS[(i + 1) % GIVE_AMOUNTS.length];
                return giveAmount;
            }
        }
        giveAmount = 64;
        return giveAmount;
    }

    /** True: drop the item on the ground where you're looking. False: put it in your inventory. */
    public static synchronized boolean giveToGround() {
        return giveToGround;
    }

    public static synchronized void toggleGiveToGround() {
        giveToGround = !giveToGround;
    }
}
