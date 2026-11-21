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
