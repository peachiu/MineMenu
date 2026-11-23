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
        instance.clamp();
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
