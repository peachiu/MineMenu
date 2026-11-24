package com.minemenu.ui;

import com.google.gson.Gson;
import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.core.CheatConfig;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cheat profiles: named sets of hacks (and their settings) you can load with one click, and share with
 * friends as a text code ("MMB1:" + base64 of JSON).
 */
public final class Profiles {
    private static final Gson GSON = new Gson();
    private static final String PREFIX = "MMB1:";

    private static final class Shared {
        String name;
        List<String> enabled = new ArrayList<>();
        Map<String, Double> settings = new HashMap<>();
    }

    private Profiles() {
    }

    public static void saveCurrent(String name) {
        List<String> enabled = new ArrayList<>();
        for (Feature feature : CheatState.enabled()) {
            enabled.add(feature.name());
        }
        CheatConfig.get().profiles.put(name, enabled);
        CheatConfig.markDirty();
    }

    public static void load(String name) {
        List<String> enabled = CheatConfig.get().profiles.get(name);
        if (enabled == null) {
            return;
        }
        CheatState.setAll(false);
        for (String id : enabled) {
            Feature feature = CheatConfig.feature(id);
            if (feature != null && !CheatState.isOn(feature)) {
                CheatState.toggle(feature);
            }
        }
    }

    public static void delete(String name) {
        CheatConfig.get().profiles.remove(name);
        CheatConfig.markDirty();
    }

    /** The share code of a profile, with the settings of the hacks in it. */
    public static String export(String name) {
        Shared shared = new Shared();
        shared.name = name;
        shared.enabled = new ArrayList<>(CheatConfig.get().profiles.getOrDefault(name, List.of()));
        for (Map.Entry<String, Double> setting : CheatConfig.get().settings.entrySet()) {
            String feature = setting.getKey().split("\\.")[0];
            if (shared.enabled.contains(feature)) {
                shared.settings.put(setting.getKey(), setting.getValue());
            }
        }
        return PREFIX + Base64.getEncoder().encodeToString(GSON.toJson(shared).getBytes(StandardCharsets.UTF_8));
    }

    /** Adds the profile in a share code; returns its name, or null if the code isn't valid. */
    public static String importCode(String code) {
        try {
            String text = code.trim();
            if (!text.startsWith(PREFIX)) {
                return null;
            }
            String json = new String(Base64.getDecoder().decode(text.substring(PREFIX.length())), StandardCharsets.UTF_8);
            Shared shared = GSON.fromJson(json, Shared.class);
            if (shared == null || shared.name == null || shared.enabled == null) {
                return null;
            }
            List<String> known = new ArrayList<>();
            for (String id : shared.enabled) {
                if (CheatConfig.feature(id) != null) {
                    known.add(id);
                }
            }
            String name = shared.name.length() > 24 ? shared.name.substring(0, 24) : shared.name;
            CheatConfig.get().profiles.put(name, known);
            if (shared.settings != null) {
                for (Map.Entry<String, Double> setting : shared.settings.entrySet()) {
                    if (setting.getValue() != null && Double.isFinite(setting.getValue())) {
                        CheatConfig.get().settings.put(setting.getKey(), setting.getValue());
                    }
                }
            }
            CheatConfig.markDirty();
            return name;
        } catch (Exception e) {
            return null;
        }
    }
}
