package com.mineboost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MineBoost's own settings (the ones vanilla doesn't have), saved to config/mineboost.json.
 * Read every frame by the mixins, so keep the fields plain and cheap to read.
 */
public final class BoostConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineBoost");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mineboost.json");

    /** Slider value meaning "no limit" for the entity distance. */
    public static final int ENTITY_DISTANCE_UNLIMITED = 17;
    /** Slider value for the block entity distance that equals vanilla (64 blocks). */
    public static final int BLOCK_ENTITY_DISTANCE_VANILLA = 8;

    private static BoostConfig instance = new BoostConfig();

    /** Entities farther than this many chunks-of-8-blocks aren't drawn. 17 = unlimited. */
    public int entityDistance = ENTITY_DISTANCE_UNLIMITED;
    /** Chests, signs, banners etc. farther than this many 8-block steps aren't drawn. 8 = vanilla. */
    public int blockEntityDistance = BLOCK_ENTITY_DISTANCE_VANILLA;
    public boolean disableParticles = false;
    public boolean hideWeather = false;
    public boolean animatedTextures = true;
    /** FPS cap while the game isn't the focused window. 0 = no cap. */
    public int backgroundFps = 15;
    public boolean fpsOverlay = false;
    /** Smart Culling: skip drawing entities and chests that are hidden behind solid blocks. */
    public boolean smartCulling = true;
    /** Smart Render Distance: FPS to aim for (one of {@link #FPS_TARGETS}); 0 = off. */
    public int fpsTarget = 0;
    /** The highest render distance Smart Render Distance may go back up to. */
    public int autoRenderMax = 12;
    /** Most particles allowed at once (one of {@link #PARTICLE_LIMITS}); -1 = no limit. */
    public int particleLimit = -1;

    // --- MineBoost 2.2 ---
    /** Draw only this many of the closest entities (one of {@link #MAX_ENTITIES}); -1 = all. */
    public int maxEntities = -1;
    /**
     * Dropped items farther than this many blocks aren't drawn (one of {@link #ITEM_DISTANCES}); -1 = no limit.
     * Vanilla already stops at about 16 blocks (items are small), so only shorter distances make sense.
     */
    public int itemDistance = -1;
    /** Draw a stack of dropped items as one item instead of up to five. */
    public boolean simpleItems = false;
    /** Particles farther than this many blocks aren't created (one of {@link #DISTANCES}); -1 = no limit. */
    public int particleRange = -1;
    public boolean hideDecorations = false;
    public boolean hideBeaconBeams = false;
    public boolean hideSky = false;
    public boolean hideNameTags = false;
    public boolean hideVignette = false;
    /** FPS cap while a menu (inventory, pause menu...) is open in a world; 0 = off. */
    public int menuFps = 0;

    // --- MineBoost 2.3 (new fields have defaults, so older mineboost.json files still load) ---
    /** Simple Far Mobs: armor, held items and other extra layers aren't drawn on mobs farther than this; -1 = off. */
    public int farMobDetail = -1;
    /** Idle Mode: FPS cap after {@link #idleSeconds} without any input (keys, mouse); 0 = off. */
    public int idleFps = 0;
    public int idleSeconds = 30;
    /** The last benchmark, so the next one can show what changed. */
    public Benchmark.Result lastBenchmark = null;

    // --- MineBoost 2.4 (MineMeBoost 3.4) ---
    /** File format version: 0 = 2.3 or older (no field), 1 = 2.4. See {@link #migrate()}. */
    public int configVersion = 0;
    /** Render Scale: the world is drawn at this % of the window's resolution (HUD stays sharp). 100 = off. */
    public int renderScale = 100;
    /** Upscale filter: true = Nearest (pixelated, fastest), false = Linear (smooth). */
    public boolean renderScaleNearest = false;
    /** Dynamic Render Scale: moves between {@link #renderScaleMin} and {@link #renderScale} to hold the FPS target. */
    public boolean dynamicRenderScale = false;
    public int renderScaleMin = 50;
    /** Section Culling: entities in chunk sections the game already knows are hidden (caves...) aren't drawn. */
    public boolean sectionCulling = false;
    /** Experimental: far, off-screen mobs are ticked (animated) every 4th tick only. */
    public boolean farEntityTicks = false;
    /** Smooth Fog Edge: fog starts earlier so chunks appearing at the edge fade in. */
    public boolean smoothFog = false;
    /** Battery Saver: 30 FPS, Render Scale at most 70%, at most 500 particles. */
    public boolean batterySaver = false;
    /** Most sounds playing at once; 0 = no limit. */
    public int maxSounds = 0;
    /** Small frame-time graph under the FPS Overlay. */
    public boolean frameGraph = false;
    /** Log frames slower than 50 ms to config/minemeboost/spikes.log. */
    public boolean spikeLog = true;
    /** Context rules: situation (NETHER, END, CAVE, CROWD, ELYTRA) -> preset name; missing = leave as is. */
    public java.util.Map<String, String> contextPresets = new java.util.HashMap<>();
    public boolean contextRules = false;

    public static final int[] RENDER_SCALES = {50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100};
    public static final int[] MAX_SOUNDS = {0, 16, 32, 64};

    public static final int[] FAR_MOB_DETAIL = {12, 16, 24, 32, 48, -1};
    public static final int[] IDLE_FPS = {0, 5, 10, 15, 30};
    public static final int[] IDLE_SECONDS = {15, 30, 60, 120, 300};

    public static final int[] MAX_ENTITIES = {25, 50, 100, 200, 400, -1};
    public static final int[] DISTANCES = {8, 16, 24, 32, 48, 64, -1};
    public static final int[] ITEM_DISTANCES = {4, 6, 8, 12, -1};
    public static final int[] MENU_FPS = {0, 30, 60};

    public static final int[] FPS_TARGETS = {0, 30, 45, 60, 75, 90, 120, 144};
    public static final int[] PARTICLE_LIMITS = {250, 500, 1000, 2000, 4000, -1};

    public static BoostConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try {
                BoostConfig loaded = GSON.fromJson(Files.readString(FILE), BoostConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (Exception e) {
                LOGGER.warn("Couldn't read {}, using defaults", FILE, e);
            }
        }
        instance.migrate();
        instance.clamp();
    }

    /** Brings files from older versions up to date without losing any value. */
    private void migrate() {
        if (configVersion < 1) {
            // 2.3 and older: the new fields already have their defaults; nothing to convert.
            configVersion = 1;
        }
        if (contextPresets == null) {
            contextPresets = new java.util.HashMap<>();
        }
    }

    /** Render Scale actually used now (Battery Saver caps it at 70%). */
    public int effectiveRenderScale() {
        return batterySaver ? Math.min(renderScale, 70) : renderScale;
    }

    /** Max Particles actually used now (Battery Saver caps it at 500). */
    public int effectiveParticleLimit() {
        if (!batterySaver) {
            return particleLimit;
        }
        return particleLimit > 0 ? Math.min(particleLimit, 500) : 500;
    }

    public static String toJson(BoostConfig config) {
        return GSON.toJson(config);
    }

    public static BoostConfig fromJson(String json) {
        BoostConfig loaded = GSON.fromJson(json, BoostConfig.class);
        if (loaded == null) {
            loaded = new BoostConfig();
        }
        loaded.migrate();
        loaded.clamp();
        return loaded;
    }

    /** Swaps in other settings (a profile, a restored state), but keeps things that aren't settings. */
    public static void replaceKeepingHistory(BoostConfig other) {
        other.lastBenchmark = instance.lastBenchmark;
        other.contextPresets = instance.contextPresets;
        other.contextRules = instance.contextRules;
        instance = other;
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (IOException e) {
            LOGGER.warn("Couldn't save {}", FILE, e);
        }
    }

    private void clamp() {
        entityDistance = Math.max(2, Math.min(ENTITY_DISTANCE_UNLIMITED, entityDistance));
        blockEntityDistance = Math.max(1, Math.min(BLOCK_ENTITY_DISTANCE_VANILLA, blockEntityDistance));
        backgroundFps = Math.max(0, Math.min(60, backgroundFps));
        if (indexOf(FPS_TARGETS, fpsTarget) < 0) {
            fpsTarget = 0;
        }
        if (indexOf(PARTICLE_LIMITS, particleLimit) < 0) {
            particleLimit = -1;
        }
        autoRenderMax = Math.max(2, Math.min(32, autoRenderMax));
        if (indexOf(MAX_ENTITIES, maxEntities) < 0) {
            maxEntities = -1;
        }
        if (indexOf(ITEM_DISTANCES, itemDistance) < 0) {
            itemDistance = -1;
        }
        if (indexOf(DISTANCES, particleRange) < 0) {
            particleRange = -1;
        }
        if (indexOf(MENU_FPS, menuFps) < 0) {
            menuFps = 0;
        }
        if (indexOf(FAR_MOB_DETAIL, farMobDetail) < 0) {
            farMobDetail = -1;
        }
        if (indexOf(IDLE_FPS, idleFps) < 0) {
            idleFps = 0;
        }
        if (indexOf(IDLE_SECONDS, idleSeconds) < 0) {
            idleSeconds = 30;
        }
        renderScale = Math.max(50, Math.min(100, renderScale / 5 * 5));
        renderScaleMin = Math.max(50, Math.min(renderScale, renderScaleMin / 5 * 5));
        if (indexOf(MAX_SOUNDS, maxSounds) < 0) {
            maxSounds = 0;
        }
    }

    public static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /** Removes particles already on screen, so "Disable All Particles" takes effect instantly. */
    public static void clearParticles() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.particleManager != null && client.world != null) {
            // setWorld with the same world is the public way to clear all particles.
            client.particleManager.setWorld(client.world);
        }
    }

    /** Max distance in blocks for drawing entities, or -1 for no limit. */
    public double entityDistanceBlocks() {
        return entityDistance >= ENTITY_DISTANCE_UNLIMITED ? -1 : entityDistance * 8.0;
    }

    /** Max distance in blocks for drawing block entities. */
    public double blockEntityDistanceBlocks() {
        return blockEntityDistance * 8.0;
    }
}
