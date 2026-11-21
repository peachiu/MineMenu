package com.mineboost;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The MineBoost menu (press "."): presets on top, then MineBoost's own boosts,
 * then the vanilla graphics settings that affect FPS the most.
 */
public class BoostScreen extends GameOptionsScreen {
    private final int mipmapsOnOpen;
    private TextWidget fpsLine;

    public BoostScreen(Screen parent, GameOptions options) {
        super(parent, options, Text.literal("MineBoost").formatted(Formatting.GREEN, Formatting.BOLD));
        this.mipmapsOnOpen = options.getMipmapLevels().getValue();
    }

    @Override
    protected void initHeader() {
        layout.setHeaderHeight(58);
        DirectionalLayoutWidget header = DirectionalLayoutWidget.vertical().spacing(4);
        header.getMainPositioner().alignHorizontalCenter();

        DirectionalLayoutWidget titleRow = DirectionalLayoutWidget.horizontal().spacing(8);
        titleRow.add(new TextWidget(this.title, this.textRenderer));
        fpsLine = titleRow.add(new TextWidget(200, 9, fpsText(), this.textRenderer).alignLeft());
        header.add(titleRow);

        DirectionalLayoutWidget presets = DirectionalLayoutWidget.horizontal().spacing(4);
        for (Preset preset : Preset.values()) {
            boolean active = preset.isActive(this.client);
            MutableText label = Text.literal(preset.displayName()).formatted(preset.color());
            if (active) {
                label = Text.literal("✔ ").formatted(Formatting.WHITE).append(label);
            }
            presets.add(ButtonWidget.builder(label, b -> applyPreset(preset))
                    .width(62)
                    .tooltip(Tooltip.of(Text.literal(preset.description()
                            + (active ? "\n\nThis preset is active." : "\n\nClick to apply."))))
                    .build());
        }
        header.add(presets);
        layout.addHeader(header);
    }

    @Override
    protected void addOptions() {
        // MineBoost extras first: these are the ones vanilla doesn't have.
        this.body.addAll(BoostOptions.all());

        // Then the vanilla settings that matter most for FPS, biggest impact first.
        GameOptions o = this.gameOptions;
        this.body.addAll(
                o.getViewDistance(), o.getSimulationDistance(),
                o.getGraphicsMode(), o.getMaxFps(),
                o.getEnableVsync(), o.getChunkBuilderMode(),
                o.getAo(), o.getCloudRenderMode(),
                o.getParticles(), o.getEntityShadows(),
                o.getEntityDistanceScaling(), o.getBiomeBlendRadius(),
                o.getMipmapLevels(), o.getMenuBackgroundBlurriness());
    }

    private void applyPreset(Preset preset) {
        if (this.body != null) {
            this.body.applyAllPendingValues();
        }
        preset.apply(this.client);
        clearAndInit(); // refresh every slider and button to the new values
    }

    private Text fpsText() {
        int fps = this.client != null ? this.client.getCurrentFps() : 0;
        Formatting color = fps >= 60 ? Formatting.GREEN : fps >= 30 ? Formatting.YELLOW : Formatting.RED;
        return Text.literal("Current FPS: ").formatted(Formatting.GRAY)
                .append(Text.literal(String.valueOf(fps)).formatted(color, Formatting.BOLD));
    }

    @Override
    public void tick() {
        if (fpsLine != null) {
            fpsLine.setMessage(fpsText());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        // Same as vanilla's Video Settings: mipmap changes need a texture reload.
        int mipmaps = this.gameOptions.getMipmapLevels().getValue();
        if (mipmaps != mipmapsOnOpen) {
            this.client.setMipmapLevels(mipmaps);
            this.client.reloadResourcesConcurrently();
        }
        BoostConfig.save();
        super.removed();
    }

    /** MineBoost's own settings as vanilla-style options, so they look and work like the rest. */
    static final class BoostOptions {
        private BoostOptions() {
        }

        static SimpleOption<?>[] all() {
            BoostConfig c = BoostConfig.get();
            return new SimpleOption<?>[]{
                    new SimpleOption<>("mineboost.option.entity_distance",
                            SimpleOption.constantTooltip(Text.literal(
                                    "Mobs, animals, items and item frames farther than this aren't drawn. "
                                            + "One of the biggest boosts near farms and mob grinders.")),
                            (prefix, value) -> GameOptions.getGenericValueText(prefix,
                                    value >= BoostConfig.ENTITY_DISTANCE_UNLIMITED
                                            ? Text.literal("Unlimited") : Text.literal(value * 8 + " blocks")),
                            new SimpleOption.ValidatingIntSliderCallbacks(2, BoostConfig.ENTITY_DISTANCE_UNLIMITED),
                            c.entityDistance,
                            value -> c.entityDistance = value),
                    new SimpleOption<>("mineboost.option.block_entity_distance",
                            SimpleOption.constantTooltip(Text.literal(
                                    "Chests, signs, banners, heads and beds farther than this aren't drawn. "
                                            + "Great for big bases and storage rooms.")),
                            (prefix, value) -> GameOptions.getGenericValueText(prefix,
                                    value >= BoostConfig.BLOCK_ENTITY_DISTANCE_VANILLA
                                            ? Text.literal("Normal") : Text.literal(value * 8 + " blocks")),
                            new SimpleOption.ValidatingIntSliderCallbacks(1, BoostConfig.BLOCK_ENTITY_DISTANCE_VANILLA),
                            c.blockEntityDistance,
                            value -> c.blockEntityDistance = value),
                    SimpleOption.ofBoolean("mineboost.option.disable_particles",
                            SimpleOption.constantTooltip(Text.literal(
                                    "No particles at all: no explosions, smoke, block breaking or potion swirls. "
                                            + "Stronger than vanilla's Particles: Minimal.")),
                            c.disableParticles,
                            value -> {
                                c.disableParticles = value;
                                if (value) {
                                    BoostConfig.clearParticles(); // remove the ones already on screen too
                                }
                            }),
                    SimpleOption.ofBoolean("mineboost.option.hide_weather",
                            SimpleOption.constantTooltip(Text.literal(
                                    "Don't draw rain and snow. It still rains (crops grow, fire goes out); "
                                            + "you just don't see it.")),
                            c.hideWeather,
                            value -> c.hideWeather = value),
                    SimpleOption.ofBoolean("mineboost.option.animated_textures",
                            SimpleOption.constantTooltip(Text.literal(
                                    "Turn off to freeze water, lava, fire and portal animations. Small boost.")),
                            c.animatedTextures,
                            value -> c.animatedTextures = value),
                    new SimpleOption<>("mineboost.option.background_fps",
                            SimpleOption.constantTooltip(Text.literal(
                                    "FPS limit when Minecraft isn't the window you're using (alt-tab). "
                                            + "Minimized always drops to 1 FPS. Frees your PC for OBS, Discord or "
                                            + "your browser.")),
                            (prefix, value) -> GameOptions.getGenericValueText(prefix,
                                    value == 0 ? Text.literal("Off") : Text.literal(value + " FPS")),
                            new SimpleOption.ValidatingIntSliderCallbacks(0, 60),
                            c.backgroundFps,
                            value -> c.backgroundFps = value),
                    SimpleOption.ofBoolean("mineboost.option.fps_overlay",
                            SimpleOption.constantTooltip(Text.literal(
                                    "Show FPS, average FPS and 1% low in the top-right corner. "
                                            + "The 1% low tells you how bad the stutters are.")),
                            c.fpsOverlay,
                            value -> c.fpsOverlay = value),
            };
        }
    }
}
