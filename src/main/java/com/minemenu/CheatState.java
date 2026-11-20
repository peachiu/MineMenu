package com.minemenu;

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
}
