package com.mineboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.ChunkBuilderMode;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.util.Formatting;

/**
 * One-click setting bundles, from "make it run at all" to "make it pretty".
 * Max FPS, VSync, GUI scale and brightness are personal, so presets leave them alone.
 */
public enum Preset {
    TOASTER("Toaster", Formatting.DARK_RED,
            "The absolute minimum: 2 chunk view distance, nothing extra drawn. For PCs that can barely run Minecraft.",
            GraphicsMode.FAST, 2, 5, false, CloudRenderMode.OFF, ParticlesMode.MINIMAL, false, 0, 0, 0.5, 0,
            2, 1, true, true, false, 5, true),
    POTATO("Potato", Formatting.RED,
            "Maximum FPS for very weak PCs. Looks rough: short view distance, no particles, no rain.",
            GraphicsMode.FAST, 4, 5, false, CloudRenderMode.OFF, ParticlesMode.MINIMAL, false, 0, 0, 0.5, 0,
            4, 2, true, true, false, 5, true),
    LOW("Low", Formatting.GOLD,
            "Big FPS boost that still looks like Minecraft.",
            GraphicsMode.FAST, 6, 6, false, CloudRenderMode.OFF, ParticlesMode.DECREASED, false, 1, 1, 0.75, 0,
            6, 4, false, true, true, 10, true),
    BALANCED("Balanced", Formatting.YELLOW,
            "Good looks and smooth FPS on most PCs.",
            GraphicsMode.FANCY, 10, 8, true, CloudRenderMode.FAST, ParticlesMode.DECREASED, true, 2, 2, 1.0, 3,
            10, 6, false, false, true, 15, true),
    HIGH("High", Formatting.GREEN,
            "Close to Minecraft's defaults, a bit farther. For decent PCs.",
            GraphicsMode.FANCY, 16, 12, true, CloudRenderMode.FANCY, ParticlesMode.ALL, true, 3, 4, 1.0, 5,
            BoostConfig.ENTITY_DISTANCE_UNLIMITED, BoostConfig.BLOCK_ENTITY_DISTANCE_VANILLA, false, false, true, 30, true),
    ULTRA("Ultra", Formatting.AQUA,
            "Everything turned up, long view distance. For strong PCs.",
            GraphicsMode.FANCY, 24, 16, true, CloudRenderMode.FANCY, ParticlesMode.ALL, true, 5, 4, 1.5, 5,
            BoostConfig.ENTITY_DISTANCE_UNLIMITED, BoostConfig.BLOCK_ENTITY_DISTANCE_VANILLA, false, false, true, 30, false);

    private final String displayName;
    private final Formatting color;
    private final String description;
    // vanilla
    private final GraphicsMode graphics;
    private final int renderDistance;
    private final int simulationDistance;
    private final boolean smoothLighting;
    private final CloudRenderMode clouds;
    private final ParticlesMode particles;
    private final boolean entityShadows;
    private final int biomeBlend;
    private final int mipmaps;
    private final double entityDistanceScaling;
    private final int menuBlur;
    // MineBoost
    private final int entityDistance;
    private final int blockEntityDistance;
    private final boolean disableParticles;
    private final boolean hideWeather;
    private final boolean animatedTextures;
    private final int backgroundFps;
    private final boolean smartCulling;

    Preset(String displayName, Formatting color, String description,
           GraphicsMode graphics, int renderDistance, int simulationDistance, boolean smoothLighting,
           CloudRenderMode clouds, ParticlesMode particles, boolean entityShadows, int biomeBlend, int mipmaps,
           double entityDistanceScaling, int menuBlur,
           int entityDistance, int blockEntityDistance, boolean disableParticles, boolean hideWeather,
           boolean animatedTextures, int backgroundFps, boolean smartCulling) {
        this.displayName = displayName;
        this.color = color;
        this.description = description;
        this.graphics = graphics;
        this.renderDistance = renderDistance;
        this.simulationDistance = simulationDistance;
        this.smoothLighting = smoothLighting;
        this.clouds = clouds;
        this.particles = particles;
        this.entityShadows = entityShadows;
        this.biomeBlend = biomeBlend;
        this.mipmaps = mipmaps;
        this.entityDistanceScaling = entityDistanceScaling;
        this.menuBlur = menuBlur;
        this.entityDistance = entityDistance;
        this.blockEntityDistance = blockEntityDistance;
        this.disableParticles = disableParticles;
        this.hideWeather = hideWeather;
        this.animatedTextures = animatedTextures;
        this.backgroundFps = backgroundFps;
        this.smartCulling = smartCulling;
    }

    public String displayName() {
        return displayName;
    }

    public Formatting color() {
        return color;
    }

    public String description() {
        return description;
    }

    /** Applies the preset. Mipmaps are applied by the caller (they need a resource reload). */
    public void apply(MinecraftClient client) {
        GameOptions o = client.options;
        set(o.getGraphicsMode(), graphics);
        set(o.getViewDistance(), clampedRenderDistance(o));
        set(o.getSimulationDistance(), simulationDistance);
        set(o.getAo(), smoothLighting);
        set(o.getCloudRenderMode(), clouds);
        set(o.getParticles(), particles);
        set(o.getEntityShadows(), entityShadows);
        set(o.getBiomeBlendRadius(), biomeBlend);
        set(o.getMipmapLevels(), mipmaps);
        set(o.getEntityDistanceScaling(), entityDistanceScaling);
        set(o.getMenuBackgroundBlurriness(), menuBlur);
        set(o.getChunkBuilderMode(), ChunkBuilderMode.NONE); // "Threaded": never blocks a frame
        o.write();

        BoostConfig c = BoostConfig.get();
        c.entityDistance = entityDistance;
        c.blockEntityDistance = blockEntityDistance;
        c.disableParticles = disableParticles;
        c.hideWeather = hideWeather;
        c.animatedTextures = animatedTextures;
        c.backgroundFps = backgroundFps;
        c.smartCulling = smartCulling;
        // Smart Render Distance (if on) now works below this preset's distance.
        SmartRenderDistance.restartFrom(clampedRenderDistance(o));
        BoostConfig.save();
        if (disableParticles) {
            BoostConfig.clearParticles();
        }
    }

    /** True if every setting this preset controls currently matches it. */
    public boolean isActive(MinecraftClient client) {
        GameOptions o = client.options;
        BoostConfig c = BoostConfig.get();
        return o.getGraphicsMode().getValue() == graphics
                // With Smart Render Distance on, the distance moves by itself: compare its maximum instead.
                && (c.fpsTarget > 0 ? c.autoRenderMax : o.getViewDistance().getValue()) == clampedRenderDistance(o)
                && o.getSimulationDistance().getValue() == simulationDistance
                && o.getAo().getValue() == smoothLighting
                && o.getCloudRenderMode().getValue() == clouds
                && o.getParticles().getValue() == particles
                && o.getEntityShadows().getValue() == entityShadows
                && o.getBiomeBlendRadius().getValue() == biomeBlend
                && o.getMipmapLevels().getValue() == mipmaps
                && o.getEntityDistanceScaling().getValue() == entityDistanceScaling
                && o.getMenuBackgroundBlurriness().getValue() == menuBlur
                && c.entityDistance == entityDistance
                && c.blockEntityDistance == blockEntityDistance
                && c.disableParticles == disableParticles
                && c.hideWeather == hideWeather
                && c.animatedTextures == animatedTextures
                && c.backgroundFps == backgroundFps
                && c.smartCulling == smartCulling;
    }

    /** Same rule as vanilla: under 1 GB of memory caps render distance at 16. */
    private static int maxRenderDistance() {
        return Runtime.getRuntime().maxMemory() >= 1_000_000_000L ? 32 : 16;
    }

    private int clampedRenderDistance(GameOptions o) {
        return Math.min(renderDistance, maxRenderDistance());
    }

    private static <T> void set(SimpleOption<T> option, T value) {
        if (!value.equals(option.getValue())) {
            option.setValue(value);
        }
    }
}
