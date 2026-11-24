package com.minemenu.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minemenu.CheatState;
import com.minemenu.Feature;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MineMenu's saved state: config/minemeboost/cheats.json. Which hacks are on, favorites, keys, settings,
 * profiles, macros, theme. New fields always get a default, so older files keep loading ({@link #configVersion}).
 */
public final class CheatConfig {
    public static final int CURRENT_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger("MineMenu");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("minemeboost");
    private static final Path FILE = DIR.resolve("cheats.json");

    private static CheatConfig instance = new CheatConfig();
    private static transient boolean dirty;
    private static int lastStateHash;

    public int configVersion = CURRENT_VERSION;
    public List<String> enabled = new ArrayList<>();
    public List<String> favorites = new ArrayList<>();
    /** "feature:FLY" or "action:DAY" -> GLFW key code. */
    public Map<String, Integer> keys = new HashMap<>();
    public Map<String, Double> settings = new HashMap<>();
    /** Text settings: morph mob, lucky block, magnet filter... */
    public Map<String, String> texts = new HashMap<>();
    /** Cheat profiles: name -> enabled hacks. */
    public Map<String, List<String>> profiles = new LinkedHashMap<>();
    public List<Macro> macros = new ArrayList<>();
    /** Item Creator favorites, as their specs (JSON text). */
    public List<String> savedItems = new ArrayList<>();
    /** Mob Creator presets (JSON text). */
    public List<String> mobPresets = new ArrayList<>();
    public String theme = "dark";
    public int accent = 0xFF55FF;
    public boolean animations = true;
    public boolean hudVisible = true;
    public boolean fpsVisible = false;
    /** The version whose "What's new" screen was last shown. */
    public String lastSeenVersion = "";

    /** A recorded list of steps ("feature:FLY", "action:DAY", "cmd:time set day", "wait:20") on a key. */
    public static final class Macro {
        public String name = "Macro";
        public int key = -1;
        public List<String> steps = new ArrayList<>();
    }

    public static CheatConfig get() {
        return instance;
    }

    /** Loads the file and turns the remembered hacks back on (unless Safe Mode says not to). */
    public static void load(boolean restoreHacks) {
        try {
            if (Files.exists(FILE)) {
                CheatConfig loaded = GSON.fromJson(Files.readString(FILE), CheatConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Couldn't read {}, starting fresh (the old file is kept as cheats.json.broken)", FILE, e);
            try {
                Files.copy(FILE, FILE.resolveSibling("cheats.json.broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
        }
        instance.fix();
        if (restoreHacks) {
            for (String name : instance.enabled) {
                Feature feature = feature(name);
                if (feature != null && feature.persists() && !CheatState.isOn(feature)) {
                    CheatState.toggle(feature);
                }
            }
        }
        if (CheatState.hudVisible() != instance.hudVisible) {
            CheatState.toggleHud();
        }
        if (CheatState.fpsVisible() != instance.fpsVisible) {
            CheatState.toggleFps();
        }
        lastStateHash = stateHash();
    }

    /** Null lists from hand-edited files, thread-safe maps, version bump. */
    private void fix() {
        if (enabled == null) enabled = new ArrayList<>();
        if (favorites == null) favorites = new ArrayList<>();
        keys = keys == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(keys);
        settings = settings == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(settings);
        texts = texts == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(texts);
        if (profiles == null) profiles = new LinkedHashMap<>();
        if (macros == null) macros = new ArrayList<>();
        if (savedItems == null) savedItems = new ArrayList<>();
        if (mobPresets == null) mobPresets = new ArrayList<>();
        if (theme == null) theme = "dark";
        if (lastSeenVersion == null) lastSeenVersion = "";
        configVersion = CURRENT_VERSION;
    }

    public static Feature feature(String name) {
        try {
            return Feature.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null; // a hack from a newer or older version
        }
    }

    public static void markDirty() {
        dirty = true;
    }

    private static int stateHash() {
        return CheatState.enabled().hashCode() * 31 + (CheatState.hudVisible() ? 1 : 0) + (CheatState.fpsVisible() ? 2 : 0);
    }

    /** Called every client tick: saves (at most once a second) when something changed. */
    public static void tick(int clientTicks) {
        if (clientTicks % 20 != 0) {
            return;
        }
        int hash = stateHash();
        if (hash != lastStateHash) {
            lastStateHash = hash;
            instance.enabled = new ArrayList<>();
            for (Feature feature : CheatState.enabled()) {
                instance.enabled.add(feature.name());
            }
            instance.hudVisible = CheatState.hudVisible();
            instance.fpsVisible = CheatState.fpsVisible();
            dirty = true;
        }
        if (dirty) {
            save();
        }
    }

    public static synchronized void save() {
        dirty = false;
        try {
            Files.createDirectories(DIR);
            Path tmp = FILE.resolveSibling("cheats.json.tmp");
            Files.writeString(tmp, GSON.toJson(instance));
            Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOGGER.warn("Couldn't save {}", FILE, e);
        }
    }

    // --- small helpers ---

    public double setting(String key, double defaultValue) {
        Double value = settings.get(key);
        return value == null ? defaultValue : value;
    }

    public void setSetting(String key, double value) {
        settings.put(key, value);
        dirty = true;
    }

    public String text(String key, String defaultValue) {
        return texts.getOrDefault(key, defaultValue);
    }

    public void setText(String key, String value) {
        texts.put(key, value);
        dirty = true;
    }

    public boolean isFavorite(String id) {
        return favorites.contains(id);
    }

    public void toggleFavorite(String id) {
        if (!favorites.remove(id)) {
            favorites.add(id);
        }
        dirty = true;
    }

    /** Back up every MineMeBoost config file, then reset MineMenu's. Returns the backup folder. */
    public static Path resetEverything() throws java.io.IOException {
        Path backup = DIR.resolve("backup-" + java.time.LocalDateTime.now().toString().replace(':', '-').substring(0, 19));
        Files.createDirectories(backup);
        Path configDir = FabricLoader.getInstance().getConfigDir();
        for (Path file : List.of(FILE, configDir.resolve("mineboost.json"), DIR.resolve("fps-profiles.json"))) {
            if (Files.exists(file)) {
                Files.copy(file, backup.resolve(file.getFileName()));
            }
        }
        instance = new CheatConfig();
        instance.fix();
        CheatState.setAll(false);
        save();
        return backup;
    }
}
