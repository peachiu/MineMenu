package com.mineboost;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * The MineBoost menu (press "."): presets on top, then MineBoost's own boosts,
 * then the vanilla graphics settings that affect FPS the most.
 */
public class BoostScreen extends GameOptionsScreen {
    /**
     * Set by MineMeBoost (MineMenu + MineBoost in one mod): builds the tab buttons
     * shown above the menu. Null in the standalone MineBoost mod.
     */
    public static Function<Screen, List<ButtonWidget>> tabs;

    /**
     * Set by MineMeBoost: true while another FPS counter (MineMenu's Show FPS) is on,
     * so the FPS Overlay can't be turned on as well. Null in the standalone MineBoost mod.
     */
    public static BooleanSupplier otherFpsCounterOn;
    public static String otherFpsCounterReason = "Another FPS counter is on. Turn it off first.";

    private final int mipmapsOnOpen;
    private final int renderDistanceOnOpen;
    private final double initialScroll;
    /** True while being replaced by a fresh copy of itself (after applying a preset). */
    private boolean refreshing;
    private TextWidget fpsLine;

    public BoostScreen(Screen parent, GameOptions options) {
        this(parent, options, options.getMipmapLevels().getValue(), options.getViewDistance().getValue(), 0);
    }

    private BoostScreen(Screen parent, GameOptions options, int mipmapsOnOpen, int renderDistanceOnOpen,
                        double initialScroll) {
        super(parent, options, Text.literal("MineBoost").formatted(Formatting.GREEN, Formatting.BOLD));
        this.mipmapsOnOpen = mipmapsOnOpen;
        this.renderDistanceOnOpen = renderDistanceOnOpen;
        this.initialScroll = initialScroll;
    }

    @Override
    protected void initHeader() {
        List<ButtonWidget> tabButtons = tabs != null ? tabs.apply(this) : List.of();
        layout.setHeaderHeight(tabButtons.isEmpty() ? 58 : 82);
        DirectionalLayoutWidget header = DirectionalLayoutWidget.vertical().spacing(4);
        header.getMainPositioner().alignHorizontalCenter();

        if (!tabButtons.isEmpty()) {
            DirectionalLayoutWidget tabRow = DirectionalLayoutWidget.horizontal().spacing(4);
            tabButtons.forEach(tabRow::add);
            header.add(tabRow);
        }

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
                    .width(60)
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
        this.body.addAll(BoostOptions.smart(this.gameOptions));
        SimpleOption<?>[] extras = BoostOptions.all();
        this.body.addAll(extras);
        lockFpsOverlayIfNeeded(extras[extras.length - 1]);

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

    @Override
    protected void initFooter() {
        DirectionalLayoutWidget footer = DirectionalLayoutWidget.horizontal().spacing(4);
        footer.add(ButtonWidget.builder(Text.literal("Auto Preset").formatted(Formatting.AQUA), b -> {
                    applyPendingValues();
                    close();
                    AutoPreset.start(this.client);
                })
                .width(100)
                .tooltip(Tooltip.of(Text.literal("Closes the menu and tries the presets from Ultra down (about 7 seconds "
                        + "each), then keeps the best looking one that reaches your FPS Target (or 60 FPS if that's off). "
                        + "Keep playing normally while it runs.")))
                .build());
        footer.add(ButtonWidget.builder(Text.literal("Benchmark (10s)").formatted(Formatting.GREEN), b -> {
                    applyPendingValues();
                    close();
                    Benchmark.start();
                })
                .width(100)
                .tooltip(Tooltip.of(Text.literal("Closes the menu and measures your FPS for 10 seconds. "
                        + "Keep playing normally; the result shows up in chat. Try it with different presets!")))
                .build());
        footer.add(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).width(100).build());
        layout.addFooter(footer);
    }

    @Override
    protected void init() {
        super.init();
        if (this.body != null && initialScroll > 0) {
            this.body.setScrollAmount(initialScroll);
        }
    }

    /** Applies sliders that are still being dragged; call before leaving the screen. */
    public void applyPendingValues() {
        if (this.body != null) {
            this.body.applyAllPendingValues();
        }
    }

    /** Greys out the FPS Overlay while another FPS counter is on (only blocks turning it on). */
    private void lockFpsOverlayIfNeeded(SimpleOption<?> fpsOverlayOption) {
        boolean locked = otherFpsCounterOn != null && otherFpsCounterOn.getAsBoolean()
                && !BoostConfig.get().fpsOverlay;
        ClickableWidget widget = this.body.getWidgetFor(fpsOverlayOption);
        if (locked && widget != null) {
            widget.active = false;
            widget.setTooltip(Tooltip.of(Text.literal(otherFpsCounterReason)));
        }
    }

    private void applyPreset(Preset preset) {
        applyPendingValues();
        preset.apply(this.client);
        // Open a fresh copy so every slider and button shows the new values. (Re-running init()
        // on the same screen would stack a second copy of the menu on top of the first.)
        double scroll = this.body != null ? this.body.getScrollAmount() : 0;
        refreshing = true;
        this.client.setScreen(new BoostScreen(this.parent, this.gameOptions, mipmapsOnOpen,
                this.gameOptions.getViewDistance().getValue(), scroll));
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
    public void removed() {
        // Same as vanilla's Video Settings: mipmap changes need a texture reload.
        // Skipped while swapping in a refreshed copy; the copy does it when it closes.
        int mipmaps = this.gameOptions.getMipmapLevels().getValue();
        if (!refreshing && mipmaps != mipmapsOnOpen) {
            this.client.setMipmapLevels(mipmaps);
            this.client.reloadResourcesConcurrently();
        }
        // You moved the render distance slider yourself: that's Smart Render Distance's new maximum.
        int renderDistance = this.gameOptions.getViewDistance().getValue();
        if (!refreshing && BoostConfig.get().fpsTarget > 0 && renderDistance != renderDistanceOnOpen) {
            SmartRenderDistance.restartFrom(renderDistance);
        }
        BoostConfig.save();
        super.removed();
    }

    /** MineBoost's own settings as vanilla-style options, so they look and work like the rest. */
    static final class BoostOptions {
        private BoostOptions() {
        }

        /** A slider over a fixed list of values (-1 shows as "All"/"Unlimited"). */
        private static SimpleOption<Integer> listSlider(String key, String tooltip, int[] values, int current,
                                                        java.util.function.IntFunction<String> label,
                                                        java.util.function.IntConsumer setter) {
            return new SimpleOption<>(key, SimpleOption.constantTooltip(Text.literal(tooltip)),
                    (prefix, index) -> GameOptions.getGenericValueText(prefix, Text.literal(label.apply(values[index]))),
                    new SimpleOption.ValidatingIntSliderCallbacks(0, values.length - 1),
                    Math.max(0, BoostConfig.indexOf(values, current)),
                    index -> setter.accept(values[index]));
        }

        private static SimpleOption<Boolean> toggle(String key, String tooltip, boolean current, java.util.function.Consumer<Boolean> setter) {
            return SimpleOption.ofBoolean(key, SimpleOption.constantTooltip(Text.literal(tooltip)), current, setter);
        }

        /** The biggest boosts, shown first. */
        static SimpleOption<?>[] smart(GameOptions gameOptions) {
            BoostConfig c = BoostConfig.get();
            return new SimpleOption<?>[]{
                    new SimpleOption<>("mineboost.option.fps_target",
                            SimpleOption.constantTooltip(Text.literal(
                                    "Smart Render Distance. Pick the FPS you want: when the game drops below it, "
                                            + "MineBoost lowers the render distance, and raises it again (up to the "
                                            + "distance you chose) when there's FPS to spare.")),
                            (prefix, index) -> GameOptions.getGenericValueText(prefix,
                                    BoostConfig.FPS_TARGETS[index] == 0 ? Text.literal("Off")
                                            : Text.literal(BoostConfig.FPS_TARGETS[index] + " FPS")),
                            new SimpleOption.ValidatingIntSliderCallbacks(0, BoostConfig.FPS_TARGETS.length - 1),
                            Math.max(0, BoostConfig.indexOf(BoostConfig.FPS_TARGETS, c.fpsTarget)),
                            index -> {
                                boolean wasOff = c.fpsTarget == 0;
                                c.fpsTarget = BoostConfig.FPS_TARGETS[index];
                                if (wasOff && c.fpsTarget > 0) {
                                    // Start from the distance you have now; never go above it.
                                    SmartRenderDistance.restartFrom(gameOptions.getViewDistance().getValue());
                                }
                            }),
                    new SimpleOption<>("mineboost.option.particle_limit",
                            SimpleOption.constantTooltip(Text.literal(
                                    "Most particles on screen at once. Stops TNT, big farms and potion clouds "
                                            + "from tanking your FPS, while normal particles still show.")),
                            (prefix, index) -> GameOptions.getGenericValueText(prefix,
                                    BoostConfig.PARTICLE_LIMITS[index] < 0 ? Text.literal("Unlimited")
                                            : Text.literal(String.valueOf(BoostConfig.PARTICLE_LIMITS[index]))),
                            new SimpleOption.ValidatingIntSliderCallbacks(0, BoostConfig.PARTICLE_LIMITS.length - 1),
                            Math.max(0, BoostConfig.indexOf(BoostConfig.PARTICLE_LIMITS, c.particleLimit)),
                            index -> c.particleLimit = BoostConfig.PARTICLE_LIMITS[index]),
                    listSlider("mineboost.option.max_entities",
                            "Draw only this many of the closest mobs, animals and items. Huge help at farms and mob "
                                    + "grinders. Players, bosses and glowing mobs are always drawn.",
                            BoostConfig.MAX_ENTITIES, c.maxEntities, v -> v < 0 ? "All" : String.valueOf(v),
                            v -> c.maxEntities = v),
                    listSlider("mineboost.option.particle_range",
                            "Particles farther away than this aren't created at all. Rain splashes, far-away "
                                    + "explosions and farms make lots you can barely see.",
                            BoostConfig.DISTANCES, c.particleRange, v -> v < 0 ? "Any" : v + " blocks",
                            v -> c.particleRange = v),
            };
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
                    listSlider("mineboost.option.item_distance",
                            "Dropped items farther than this aren't drawn. Farms and explosions leave lots of them around.",
                            BoostConfig.DISTANCES, c.itemDistance, v -> v < 0 ? "All" : v + " blocks",
                            v -> c.itemDistance = v),
                    toggle("mineboost.option.simple_items",
                            "Draw a stack of dropped items as one item instead of a pile of up to five. "
                                    + "Up to 5x less to draw for every dropped stack.",
                            c.simpleItems, v -> c.simpleItems = v),
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
                    SimpleOption.ofBoolean("mineboost.option.smart_culling",
                            SimpleOption.constantTooltip(Text.literal(
                                    "Don't draw mobs, items, chests and signs that are completely hidden behind "
                                            + "solid blocks. Big boost in caves, villages and bases. Glass and leaves "
                                            + "never hide anything, and things close to you are always drawn.")),
                            c.smartCulling,
                            value -> c.smartCulling = value),
                    listSlider("mineboost.option.menu_fps",
                            "FPS limit while an inventory, chest or the pause menu is open. Nothing there needs full "
                                    + "speed, so your PC runs cooler and quieter. (Chat isn't limited.)",
                            BoostConfig.MENU_FPS, c.menuFps, v -> v == 0 ? "Off" : v + " FPS",
                            v -> c.menuFps = v),
                    toggle("mineboost.option.hide_sky",
                            "Don't draw the sky, sun, moon and stars. The background is just the fog colour.",
                            c.hideSky, v -> c.hideSky = v),
                    toggle("mineboost.option.hide_beacon_beams",
                            "Don't draw beacon beams (the beacon still works).",
                            c.hideBeaconBeams, v -> c.hideBeaconBeams = v),
                    toggle("mineboost.option.hide_decorations",
                            "Don't draw armor stands, item frames and paintings. Helps in decorated bases and villages.",
                            c.hideDecorations, v -> c.hideDecorations = v),
                    toggle("mineboost.option.hide_name_tags",
                            "Don't draw names above players and named mobs.",
                            c.hideNameTags, v -> c.hideNameTags = v),
                    toggle("mineboost.option.hide_vignette",
                            "Don't draw the dark shading around the edges of the screen. A small saving, and a clearer view.",
                            c.hideVignette, v -> c.hideVignette = v),
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
