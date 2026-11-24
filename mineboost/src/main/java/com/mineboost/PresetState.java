package com.mineboost;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.option.SimpleOption;

/**
 * Every setting a preset touches (vanilla and MineBoost), captured so it can be put back exactly: used by
 * context rules (leaving the Nether gives you your settings back), the A/B test and FPS profiles.
 * Plain fields, so it can be saved as JSON.
 */
public final class PresetState {
    public String graphics;
    public int renderDistance;
    public int simulationDistance;
    public boolean smoothLighting;
    public String clouds;
    public String particles;
    public boolean entityShadows;
    public int biomeBlend;
    public int mipmaps;
    public double entityDistanceScaling;
    public int menuBlur;
    /** All of MineBoost's own settings, as JSON. */
    public String boost;

    public static PresetState capture(MinecraftClient client) {
        GameOptions o = client.options;
        PresetState s = new PresetState();
        s.graphics = o.getGraphicsMode().getValue().name();
        s.renderDistance = o.getViewDistance().getValue();
        s.simulationDistance = o.getSimulationDistance().getValue();
        s.smoothLighting = o.getAo().getValue();
        s.clouds = o.getCloudRenderMode().getValue().name();
        s.particles = o.getParticles().getValue().name();
        s.entityShadows = o.getEntityShadows().getValue();
        s.biomeBlend = o.getBiomeBlendRadius().getValue();
        s.mipmaps = o.getMipmapLevels().getValue();
        s.entityDistanceScaling = o.getEntityDistanceScaling().getValue();
        s.menuBlur = o.getMenuBackgroundBlurriness().getValue();
        s.boost = BoostConfig.toJson(BoostConfig.get());
        return s;
    }

    /** Puts everything back (mipmap changes need a texture reload; done only if they changed). */
    public void restore(MinecraftClient client) {
        GameOptions o = client.options;
        set(o.getGraphicsMode(), GraphicsMode.valueOf(graphics));
        set(o.getViewDistance(), renderDistance);
        set(o.getSimulationDistance(), simulationDistance);
        set(o.getAo(), smoothLighting);
        set(o.getCloudRenderMode(), CloudRenderMode.valueOf(clouds));
        set(o.getParticles(), ParticlesMode.valueOf(particles));
        set(o.getEntityShadows(), entityShadows);
        set(o.getBiomeBlendRadius(), biomeBlend);
        boolean mipmapsChanged = o.getMipmapLevels().getValue() != mipmaps;
        set(o.getMipmapLevels(), mipmaps);
        set(o.getEntityDistanceScaling(), entityDistanceScaling);
        set(o.getMenuBackgroundBlurriness(), menuBlur);
        o.write();
        if (boost != null) {
            BoostConfig.replaceKeepingHistory(BoostConfig.fromJson(boost));
        }
        if (mipmapsChanged) {
            client.setMipmapLevels(mipmaps);
            client.reloadResourcesConcurrently();
        }
    }

    /** Throws if a shared or hand-edited profile has values Minecraft wouldn't accept. */
    public static void validate(PresetState s) {
        GraphicsMode.valueOf(s.graphics);
        CloudRenderMode.valueOf(s.clouds);
        ParticlesMode.valueOf(s.particles);
        if (s.renderDistance < 2 || s.renderDistance > 32 || s.simulationDistance < 5 || s.simulationDistance > 32
                || s.mipmaps < 0 || s.mipmaps > 4 || s.biomeBlend < 0 || s.biomeBlend > 7 || s.menuBlur < 0 || s.menuBlur > 10) {
            throw new IllegalArgumentException("value out of range");
        }
    }

    private static <T> void set(SimpleOption<T> option, T value) {
        if (!value.equals(option.getValue())) {
            option.setValue(value);
        }
    }
}
