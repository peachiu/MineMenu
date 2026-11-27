package com.minexray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MineXray's settings: minexray.json in {@link XrayHost#configDir} (presets in xray-presets/ next to it). In
 * MineMeBoostXray, the first start takes over the old MineMenu Ore Vision / Chest ESP / Mob Glow state and radii.
 */
public final class XrayConfig {
    public static final int CURRENT_VERSION = 1;
    /** config/minexray/minexray.json on its own, config/minemeboost/minexray.json in MineMeBoostXray. */
    public static Path file() {
        return XrayHost.configDir.resolve("minexray.json");
    }
    private static final Logger LOGGER = LoggerFactory.getLogger("MineXray");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static XrayConfig instance;
    private static boolean dirty;

    public int configVersion = CURRENT_VERSION;

    // Modes (on/off)
    public boolean classic;
    public boolean oreEsp;
    public boolean caveFinder;
    public boolean fluidView;
    public boolean blockEsp;
    public boolean entityEsp;
    public boolean lavaWarning;

    // Classic X-Ray
    /** How much of the hidden blocks still shows, 0-100 %. */
    public int classicOpacity = 0;
    public boolean classicFluids = true;

    // Ore ESP
    public int oreRadius = 32;
    public int blockLimit = 2000;
    public String style = EspStyle.OUTLINE_FILL.name();
    public float lineWidth = 2.0f;
    public int lineOpacity = 90;
    public int fillOpacity = 20;
    public boolean fade = true;
    public boolean tracers = true;
    public int tracerCount = 10;
    public boolean labels = true;

    // Block ESP
    public int blockEspRadius = 32;
    public Map<String, Boolean> categories = new LinkedHashMap<>();
    public Map<String, Integer> categoryColors = new LinkedHashMap<>();

    // Cave Finder
    public int caveRadius = 32;
    public int caveMaxY = 40;
    public boolean caveLargeOnly = false;
    public int caveMinVolume = 400;
    public String caveStyle = "DOTS";

    // Fluid View and Lava Warning
    public int fluidRadius = 24;
    public boolean fluidLava = true;
    public boolean fluidWater = false;
    public boolean lavaWarningSound = true;

    // Entity ESP (replaces Mob Glow)
    public int entityRadius = 64;
    public boolean entityHostile = true;
    public boolean entityPassive = true;
    public boolean entityPlayers = true;
    public boolean entityItems = false;
    public String entityStyle = "OUTLINE";
    /** Only these mob types (ids), empty = every type the switches above allow. */
    public java.util.List<String> entityOnly = new java.util.ArrayList<>();

    // HUD
    public boolean hudCounter = true;
    public boolean hudArrow = true;
    public boolean hudYGuide = false;
    public boolean hudStats = false;
    public boolean hudIndicator = true;
    public boolean minimapLayer = true;

    // Presets
    public String activePreset = "All Ores";
    public boolean autoDimension = true;
    public Map<String, String> dimensionPresets = new LinkedHashMap<>();

    // Performance
    public boolean adaptiveRange = true;
    public int memoryLimitMb = 64;

    public enum EspStyle { OUTLINE, FILLED, OUTLINE_FILL, CORNERS }

    public static XrayConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        XrayConfig loaded = null;
        boolean fresh = !Files.exists(file());
        if (!fresh) {
            try {
                loaded = GSON.fromJson(Files.readString(file()), XrayConfig.class);
            } catch (Exception e) {
                LOGGER.warn("[MineXray] Couldn't read {}, starting with defaults", file(), e);
            }
        }
        if (loaded == null) {
            loaded = new XrayConfig();
        }
        loaded.fillDefaults();
        if (fresh) {
            XrayHost.firstStart.accept(loaded); // MineMeBoostXray: take over MineMenu's old toggles
        }
        loaded.configVersion = CURRENT_VERSION;
        instance = loaded;
        save();
    }

    private void fillDefaults() {
        for (EspCategory category : EspCategory.values()) {
            if (category.oreLayer()) {
                continue;
            }
            categories.putIfAbsent(category.name(), category.defaultOn);
            categoryColors.putIfAbsent(category.name(), category.defaultColor);
        }
        dimensionPresets.putIfAbsent("minecraft:overworld", "All Ores");
        dimensionPresets.putIfAbsent("minecraft:the_nether", "Netherite Hunt");
        oreRadius = clamp(oreRadius, 8, 128);
        blockEspRadius = clamp(blockEspRadius, 8, 128);
        caveRadius = clamp(caveRadius, 8, 128);
        fluidRadius = clamp(fluidRadius, 8, 128);
        entityRadius = clamp(entityRadius, 8, 128);
        blockLimit = clamp(blockLimit, 100, 20000);
        tracerCount = clamp(tracerCount, 1, 50);
        classicOpacity = clamp(classicOpacity, 0, 100);
        lineOpacity = clamp(lineOpacity, 10, 100);
        fillOpacity = clamp(fillOpacity, 0, 100);
        memoryLimitMb = clamp(memoryLimitMb, 16, 512);
        lineWidth = Math.max(1f, Math.min(6f, lineWidth));
        try {
            EspStyle.valueOf(style);
        } catch (Exception e) {
            style = EspStyle.OUTLINE_FILL.name();
        }
        if (entityOnly == null) {
            entityOnly = new java.util.ArrayList<>();
        }
    }

    public boolean isOn(XrayMode mode) {
        return switch (mode) {
            case CLASSIC -> classic;
            case ORE_ESP -> oreEsp;
            case CAVE_FINDER -> caveFinder;
            case FLUID_VIEW -> fluidView;
            case BLOCK_ESP -> blockEsp;
            case ENTITY_ESP -> entityEsp;
            case LAVA_WARNING -> lavaWarning;
        };
    }

    public void set(XrayMode mode, boolean on) {
        switch (mode) {
            case CLASSIC -> classic = on;
            case ORE_ESP -> oreEsp = on;
            case CAVE_FINDER -> caveFinder = on;
            case FLUID_VIEW -> fluidView = on;
            case BLOCK_ESP -> blockEsp = on;
            case ENTITY_ESP -> entityEsp = on;
            case LAVA_WARNING -> lavaWarning = on;
        }
        markDirty();
    }

    public boolean categoryOn(EspCategory category) {
        return category.oreLayer() || categories.getOrDefault(category.name(), category.defaultOn);
    }

    public int categoryColor(EspCategory category) {
        return categoryColors.getOrDefault(category.name(), category.defaultColor);
    }

    public EspStyle espStyle() {
        try {
            return EspStyle.valueOf(style);
        } catch (Exception e) {
            return EspStyle.OUTLINE_FILL;
        }
    }

    public static void markDirty() {
        dirty = true;
    }

    /** Client tick: writes the file at most once a second, and only after a change. */
    public static void tick(int ticks) {
        if (dirty && ticks % 20 == 0) {
            save();
        }
    }

    public static synchronized void save() {
        dirty = false;
        if (instance == null) {
            return;
        }
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling("minexray.json.tmp");
            Files.writeString(tmp, GSON.toJson(instance));
            try {
                Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.warn("[MineXray] Couldn't save {}", file(), e);
        }
    }

    /** For tests and "Reset": back to defaults, keeping nothing. */
    public static void resetToDefaults() {
        instance = new XrayConfig();
        instance.fillDefaults();
        save();
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
