package com.minemenu.core;

import com.minemenu.Feature;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The settings each hack has (right-click its button to change them). Values live in {@link CheatConfig}. */
public final class FeatureSettings {
    private static final Map<Feature, List<Setting>> SETTINGS = new EnumMap<>(Feature.class);

    static {
        SETTINGS.put(Feature.SPEED, List.of(new Setting("multiplier", "Speed", 1.5, 10, 0.5, 3, "x")));
        SETTINGS.put(Feature.SUPER_JUMP, List.of(new Setting("strength", "Jump strength", 0.3, 3, 0.1, 1.1, "")));
        SETTINGS.put(Feature.DOUBLE_JUMP, List.of(new Setting("jumps", "Extra jumps", 1, 3, 1, 1, "")));
        SETTINGS.put(Feature.DASH, List.of(new Setting("strength", "Dash strength", 0.5, 4, 0.25, 1.5, ""),
                new Setting("cooldown", "Cooldown", 0.25, 5, 0.25, 1, "s")));
        SETTINGS.put(Feature.GRAVITY_CONTROL, List.of(new Setting("gravity", "Gravity", 0.1, 3, 0.1, 0.5, "x")));
        SETTINGS.put(Feature.LIFESTEAL, List.of(new Setting("percent", "Healing", 5, 100, 5, 25, "%")));
        SETTINGS.put(Feature.FORCE_FIELD, List.of(new Setting("radius", "Radius", 2, 10, 1, 5, "blocks")));
        SETTINGS.put(Feature.NUKER, List.of(new Setting("radius", "Radius", 1, 5, 1, 3, "blocks")));
        SETTINGS.put(Feature.ORE_VISION, List.of(new Setting("radius", "Radius", 8, 48, 4, 24, "blocks")));
        SETTINGS.put(Feature.CHEST_ESP, List.of(new Setting("radius", "Radius", 16, 64, 8, 32, "blocks")));
        SETTINGS.put(Feature.CHAOS_MODE, List.of(new Setting("minutes", "Every", 1, 10, 1, 3, "min"),
                Setting.toggle("meteors", "Meteor shower", true), Setting.toggle("tnt", "Harmless TNT rain", true),
                Setting.toggle("babies", "All mobs become babies", true), Setting.toggle("low_gravity", "Low gravity", true),
                Setting.toggle("diamonds", "Diamond rain", true), Setting.toggle("invasion", "Mob invasion", true),
                Setting.toggle("darkness", "Dark world", true), Setting.toggle("swap", "Swap with a mob", true)));
        SETTINGS.put(Feature.MINIMAP, List.of(new Setting("zoom", "Zoom", 1, 4, 1, 1, "x"),
                Setting.toggle("round", "Round map", false), Setting.toggle("mobs", "Show mobs", true),
                new Setting("size", "Size", 64, 160, 16, 96, "px")));
        SETTINGS.put(Feature.MOB_HEALTH_BARS, List.of(new Setting("range", "Range", 8, 48, 4, 24, "blocks")));
        SETTINGS.put(Feature.TERRAIN_BRUSH, List.of(new Setting("radius", "Radius", 1, 8, 1, 4, "blocks")));
        SETTINGS.put(Feature.INFO_HUD, List.of(Setting.toggle("coords", "Coordinates", true),
                Setting.toggle("direction", "Direction", true), Setting.toggle("biome", "Biome", true),
                Setting.toggle("time", "Time", true), Setting.toggle("day", "Day", true),
                Setting.toggle("speed", "Speed", false), Setting.toggle("chunk", "Chunk", false),
                Setting.toggle("light", "Light level", false)));
        SETTINGS.put(Feature.ZOOM, List.of(new Setting("start", "Starting zoom", 1.5, 10, 0.5, 4, "x")));
    }

    private FeatureSettings() {
    }

    public static List<Setting> of(Feature feature) {
        return SETTINGS.getOrDefault(feature, List.of());
    }

    public static double get(Feature feature, String key) {
        for (Setting setting : of(feature)) {
            if (setting.key().equals(key)) {
                return CheatConfig.get().setting(feature.name() + "." + key, setting.defaultValue());
            }
        }
        throw new IllegalArgumentException("No setting " + key + " on " + feature);
    }

    public static boolean is(Feature feature, String key) {
        return get(feature, key) >= 0.5;
    }

    public static void set(Feature feature, Setting setting, double value) {
        double clamped = Math.max(setting.min(), Math.min(setting.max(), value));
        CheatConfig.get().setSetting(feature.name() + "." + setting.key(), clamped);
    }
}
