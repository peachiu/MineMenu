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
        String detected = Compat.detected();
        layout.setHeaderHeight((tabButtons.isEmpty() ? 58 : 82) + (detected.isEmpty() ? 0 : 12));
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
                    .width(Math.max(40, this.textRenderer.getWidth(label) + 10))
                    .tooltip(Tooltip.of(Text.literal(preset.description()
                            + (active ? "\n\nThis preset is active." : "\n\nClick to apply."))))
                    .build());
        }
        header.add(presets);
        if (!detected.isEmpty()) {
            // Other performance mods found: say which, and what MineBoost turned off so it doesn't do the same work twice.
            List<String> off = Compat.turnedOff();
            String offText = off.isEmpty() ? "" : " (" + off.size() + " MineBoost option" + (off.size() > 1 ? "s" : "") + " off, hover)";
            String line = "Works with: " + detected + offText;
            if (this.textRenderer.getWidth(line) > this.width - 20) {
                // Too long for this window: the names go in the tooltip instead.
                line = "Works with " + detected.split(", ").length + " other FPS mods" + offText;
            }
            TextWidget compat = new TextWidget(Text.literal(line).formatted(Formatting.GRAY), this.textRenderer);
            compat.setTooltip(Tooltip.of(Text.literal("Found: " + detected + "\n\n" + (off.isEmpty()
                    ? "No conflicts: these mods and MineBoost speed up different things."
                    : "Turned off because the other mod already does it:\n- " + String.join("\n- ", off)))));
            header.add(compat);
        }
        layout.addHeader(header);
    }

    @Override
    protected void addOptions() {
        // MineBoost extras first: these are the ones vanilla doesn't have.
        this.body.addAll(BoostOptions.smart(this.gameOptions));
        SimpleOption<?>[] extras = BoostOptions.all();
        this.body.addAll(extras);
        lockFpsOverlayIfNeeded(extras[extras.length - 1]);
        if (Compat.ENTITYCULLING) {
            lock(BoostOptions.smartCullingOption, "Off: EntityCulling is installed and already hides things behind walls.");
        }
        String scaleReason = com.mineboost.renderscale.RenderScale.unavailableReason(this.client);
        if (scaleReason != null) {
            lock(BoostOptions.renderScaleOption, scaleReason);
        }
        if (Compat.SODIUM || Compat.ENTITYCULLING) {
            lock(BoostOptions.sectionCullingOption, Compat.SODIUM ? "Off: Sodium already skips entities in hidden sections."
                    : "Off: EntityCulling already hides entities you can't see.");
        }

        // Then the vanilla settings that matter most for FPS, biggest impact first.
        GameOptions o = this.gameOptions;
        List<SimpleOption<?>> vanilla = new java.util.ArrayList<>(List.of(
                o.getViewDistance(), o.getSimulationDistance(),
                o.getGraphicsMode(), o.getMaxFps(),
                o.getEnableVsync(), o.getChunkBuilderMode(),
                o.getAo(), o.getCloudRenderMode(),
                o.getParticles(), o.getEntityShadows(),
                o.getEntityDistanceScaling(), o.getBiomeBlendRadius(),
                o.getMipmapLevels(), o.getMenuBackgroundBlurriness()));
        if (Compat.SODIUM) {
            vanilla.remove(o.getChunkBuilderMode()); // Sodium ignores it
        }
        this.body.addAll(vanilla.toArray(new SimpleOption<?>[0]));
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
        footer.add(ButtonWidget.builder(Text.literal("Tools...").formatted(Formatting.YELLOW), b -> {
                    applyPendingValues();
                    this.client.setScreen(new com.mineboost.tools.BoostToolsScreen(this));
                })
                .width(100)
                .tooltip(Tooltip.of(Text.literal("Lag Doctor, A/B test of every boost, memory and JVM advice, context rules, "
                        + "FPS profiles and Battery Saver.")))
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

    /** Greys out an option another mod already handles. */
    private void lock(SimpleOption<?> option, String reason) {
        ClickableWidget widget = option != null ? this.body.getWidgetFor(option) : null;
        if (widget != null) {
            widget.active = false;
            widget.setTooltip(Tooltip.of(Text.literal(reason)));
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
        static SimpleOption<Boolean> smartCullingOption;

        private BoostOptions() {
        }

        /**
         * The "Impact" line under each tooltip: the typical FPS gain and where it shows. Measured with the
         * production jar in one busy scene (98 armored zombies 26-44 blocks away, 40 chests, 150 dropped
         * stacks), each option on/off 3 times. See docs/fps-measurements.md. Options that don't change FPS
         * while you play say so honestly.
         */
        static final String IMPACT_RENDER_SCALE = "Very high when the graphics card is the limit (integrated graphics): see the A/B test.";
        static final String IMPACT_SECTION_CULLING = "High when mobs are hidden in caves or behind hills; none in the open.";
        static final String IMPACT_FAR_TICKS = "Small; experimental.";

        private static final java.util.Map<String, String> IMPACT = java.util.Map.ofEntries(
                java.util.Map.entry("mineboost.option.fps_target", "High when you're below the target: render distance is the biggest FPS cost in Minecraft."),
                java.util.Map.entry("mineboost.option.particle_limit", "None normally. Big during TNT, potion clouds and particle farms (stops the FPS crash)."),
                java.util.Map.entry("mineboost.option.max_entities", "High in crowds: +37% FPS at 50 (measured with 250 mobs and items in view)."),
                java.util.Map.entry("mineboost.option.particle_range", "Small normally. Helps when far-away explosions, rain or farms make lots of particles."),
                java.util.Map.entry("mineboost.option.far_mob_detail", "High with armored mobs: +31% FPS and +48% 1% low at 16 blocks (measured with 98 mobs in armor)."),
                java.util.Map.entry("mineboost.option.entity_distance", "High in crowds: +30% FPS at 24 blocks (measured with 98 mobs 26-44 blocks away)."),
                java.util.Map.entry("mineboost.option.block_entity_distance", "Small: +2% with 40 chests in view. Worth it only in big storage rooms."),
                java.util.Map.entry("mineboost.option.item_distance", "Medium near lots of drops: +10% FPS at 6 blocks (measured with 150 stacks on the ground)."),
                java.util.Map.entry("mineboost.option.simple_items", "Small: +4% with 150 stacks on the ground."),
                java.util.Map.entry("mineboost.option.disable_particles", "None normally. Big during explosions and in particle-heavy places."),
                java.util.Map.entry("mineboost.option.hide_weather", "Medium while it rains or snows (none in clear weather)."),
                java.util.Map.entry("mineboost.option.animated_textures", "Small (water, lava and fire animations)."),
                java.util.Map.entry("mineboost.option.background_fps", "None while you play. Frees your PC while Minecraft is in the background."),
                java.util.Map.entry("mineboost.option.smart_culling", "High in caves, villages and bases where walls hide mobs and chests; none in open fields."),
                java.util.Map.entry("mineboost.option.idle_fps", "None while you play. Saves power and heat when you're away, so a laptop doesn't slow down from heat later."),
                java.util.Map.entry("mineboost.option.menu_fps", "None while you play. Your PC runs cooler while a menu is open."),
                java.util.Map.entry("mineboost.option.hide_sky", "Small."),
                java.util.Map.entry("mineboost.option.hide_beacon_beams", "Small, only near beacons."),
                java.util.Map.entry("mineboost.option.hide_decorations", "Medium in bases full of armor stands and item frames."),
                java.util.Map.entry("mineboost.option.hide_name_tags", "Small, only with many named mobs or players."),
                java.util.Map.entry("mineboost.option.hide_vignette", "Tiny (about one full-screen layer less per frame)."),
                java.util.Map.entry("mineboost.option.fps_overlay", "None (it only shows the numbers)."),
                java.util.Map.entry("mineboost.option.render_scale", IMPACT_RENDER_SCALE),
                java.util.Map.entry("mineboost.option.dynamic_render_scale", "Holds your FPS target by trading sharpness for FPS only when needed."),
                java.util.Map.entry("mineboost.option.section_culling", IMPACT_SECTION_CULLING),
                java.util.Map.entry("mineboost.option.far_entity_ticks", IMPACT_FAR_TICKS),
                java.util.Map.entry("mineboost.option.smooth_fog", "None (looks smoother, costs nothing)."),
                java.util.Map.entry("mineboost.option.max_sounds", "Only with very many sounds at once; couldn't be measured on the test PC (no audio)."),
                java.util.Map.entry("mineboost.option.frame_graph", "Tiny (a few small rectangles per frame)."),
                java.util.Map.entry("mineboost.option.spike_log", "None (one comparison per frame).")
        );

        /** The tooltip text plus its "Impact:" line, if there is one. */
        static Text withImpact(String key, String tooltip) {
            String impact = IMPACT.get(key);
            return Text.literal(tooltip + (impact == null ? "" : "\n\nImpact: " + impact));
        }


        /** A slider over a fixed list of values (-1 shows as "All"/"Unlimited"). */
        private static SimpleOption<Integer> listSlider(String key, String tooltip, int[] values, int current,
                                                        java.util.function.IntFunction<String> label,
                                                        java.util.function.IntConsumer setter) {
            return new SimpleOption<>(key, SimpleOption.constantTooltip(withImpact(key, tooltip)),
                    (prefix, index) -> GameOptions.getGenericValueText(prefix, Text.literal(label.apply(values[index]))),
                    new SimpleOption.ValidatingIntSliderCallbacks(0, values.length - 1),
                    Math.max(0, BoostConfig.indexOf(values, current)),
                    index -> setter.accept(values[index]));
        }

        private static SimpleOption<Boolean> toggle(String key, String tooltip, boolean current, java.util.function.Consumer<Boolean> setter) {
            return SimpleOption.ofBoolean(key, SimpleOption.constantTooltip(withImpact(key, tooltip)), current, setter);
        }

        /** The biggest boosts, shown first. */
        static SimpleOption<Integer> renderScaleOption;
        static SimpleOption<Boolean> sectionCullingOption;

        static SimpleOption<?>[] smart(GameOptions gameOptions) {
            BoostConfig c = BoostConfig.get();
            return new SimpleOption<?>[]{
                    renderScaleOption = listSlider("mineboost.option.render_scale",
                            "Render Scale: draw the world at a lower resolution and stretch it to the window. The HUD, text "
                                    + "and menus stay sharp. 75% means about half the pixels to fill, which is what slows "
                                    + "down integrated graphics the most.",
                            BoostConfig.RENDER_SCALES, c.renderScale, v -> v >= 100 ? "Off (100%)" : v + "%", v -> c.renderScale = v),
                    toggle("mineboost.option.dynamic_render_scale",
                            "Dynamic Render Scale: lowers the scale by itself when you're below your FPS Target (60 if off) "
                                    + "and raises it back (up to Render Scale) when there's FPS to spare. Changes at most once a second.",
                            c.dynamicRenderScale, v -> c.dynamicRenderScale = v),
                    new SimpleOption<>("mineboost.option.fps_target",
                            SimpleOption.constantTooltip(withImpact("mineboost.option.fps_target", 
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
                            SimpleOption.constantTooltip(withImpact("mineboost.option.particle_limit", 
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
                    listSlider("mineboost.option.far_mob_detail",
                            "Simple Far Mobs: mobs farther than this are drawn without armor, held items, saddles, "
                                    + "heads and other extra layers (each one draws the mob again). Sheep wool, glowing "
                                    + "eyes and charged creepers are kept, and players always look normal.",
                            BoostConfig.FAR_MOB_DETAIL, c.farMobDetail, v -> v < 0 ? "Off" : v + " blocks",
                            v -> c.farMobDetail = v),
                    new SimpleOption<>("mineboost.option.entity_distance",
                            SimpleOption.constantTooltip(withImpact("mineboost.option.entity_distance", 
                                    "Mobs, animals, items and item frames farther than this aren't drawn. "
                                            + "One of the biggest boosts near farms and mob grinders.")),
                            (prefix, value) -> GameOptions.getGenericValueText(prefix,
                                    value >= BoostConfig.ENTITY_DISTANCE_UNLIMITED
                                            ? Text.literal("Unlimited") : Text.literal(value * 8 + " blocks")),
                            new SimpleOption.ValidatingIntSliderCallbacks(2, BoostConfig.ENTITY_DISTANCE_UNLIMITED),
                            c.entityDistance,
                            value -> c.entityDistance = value),
                    new SimpleOption<>("mineboost.option.block_entity_distance",
                            SimpleOption.constantTooltip(withImpact("mineboost.option.block_entity_distance", 
                                    "Chests, signs, banners, heads and beds farther than this aren't drawn. "
                                            + "Great for big bases and storage rooms.")),
                            (prefix, value) -> GameOptions.getGenericValueText(prefix,
                                    value >= BoostConfig.BLOCK_ENTITY_DISTANCE_VANILLA
                                            ? Text.literal("Normal") : Text.literal(value * 8 + " blocks")),
                            new SimpleOption.ValidatingIntSliderCallbacks(1, BoostConfig.BLOCK_ENTITY_DISTANCE_VANILLA),
                            c.blockEntityDistance,
                            value -> c.blockEntityDistance = value),
                    listSlider("mineboost.option.item_distance",
                            "Dropped items farther than this aren't drawn (normally Minecraft draws them up to about "
                                    + "16 blocks). Farms and explosions leave lots of them around.",
                            BoostConfig.ITEM_DISTANCES, c.itemDistance, v -> v < 0 ? "Normal" : v + " blocks",
                            v -> c.itemDistance = v),
                    toggle("mineboost.option.simple_items",
                            "Draw a stack of dropped items as one item instead of a pile of up to five. "
                                    + "Up to 5x less to draw for every dropped stack.",
                            c.simpleItems, v -> c.simpleItems = v),
                    SimpleOption.ofBoolean("mineboost.option.disable_particles",
                            SimpleOption.constantTooltip(withImpact("mineboost.option.disable_particles", 
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
                            SimpleOption.constantTooltip(withImpact("mineboost.option.hide_weather", 
                                    "Don't draw rain and snow. It still rains (crops grow, fire goes out); "
                                            + "you just don't see it.")),
                            c.hideWeather,
                            value -> c.hideWeather = value),
                    SimpleOption.ofBoolean("mineboost.option.animated_textures",
                            SimpleOption.constantTooltip(withImpact("mineboost.option.animated_textures", 
                                    "Turn off to freeze water, lava, fire and portal animations. Small boost.")),
                            c.animatedTextures,
                            value -> c.animatedTextures = value),
                    new SimpleOption<>("mineboost.option.background_fps",
                            SimpleOption.constantTooltip(withImpact("mineboost.option.background_fps", 
                                    "FPS limit when Minecraft isn't the window you're using (alt-tab). "
                                            + "Minimized always drops to 1 FPS. Frees your PC for OBS, Discord or "
                                            + "your browser.")),
                            (prefix, value) -> GameOptions.getGenericValueText(prefix,
                                    value == 0 ? Text.literal("Off") : Text.literal(value + " FPS")),
                            new SimpleOption.ValidatingIntSliderCallbacks(0, 60),
                            c.backgroundFps,
                            value -> c.backgroundFps = value),
                    smartCullingOption = SimpleOption.ofBoolean("mineboost.option.smart_culling",
                            SimpleOption.constantTooltip(withImpact("mineboost.option.smart_culling", 
                                    "Don't draw mobs, items, chests and signs that are completely hidden behind "
                                            + "solid blocks. Big boost in caves, villages and bases. Glass and leaves "
                                            + "never hide anything, and things close to you are always drawn.")),
                            c.smartCulling,
                            value -> c.smartCulling = value),
                    listSlider("mineboost.option.idle_fps",
                            "Idle Mode: when you haven't touched the keyboard or mouse for a while (see Idle After), "
                                    + "limit the FPS to this. Anything you press brings full FPS back instantly. "
                                    + "Your laptop stays cooler, so it doesn't slow down from heat later.",
                            BoostConfig.IDLE_FPS, c.idleFps, v -> v == 0 ? "Off" : v + " FPS",
                            v -> c.idleFps = v),
                    listSlider("mineboost.option.idle_seconds",
                            "How long without input before Idle Mode starts.",
                            BoostConfig.IDLE_SECONDS, c.idleSeconds, v -> v < 60 ? v + "s" : v / 60 + " min",
                            v -> c.idleSeconds = v),
                    listSlider("mineboost.option.menu_fps",
                            "FPS limit while an inventory, chest or the pause menu is open. Nothing there needs full "
                                    + "speed, so your PC runs cooler and quieter. (Chat isn't limited.)",
                            BoostConfig.MENU_FPS, c.menuFps, v -> v == 0 ? "Off" : v + " FPS",
                            v -> c.menuFps = v),
                    toggle("mineboost.option.render_scale_filter",
                            "How Render Scale stretches the picture: ON = Nearest (sharp pixels, a bit faster), OFF = Linear (smooth).",
                            c.renderScaleNearest, v -> c.renderScaleNearest = v),
                    listSlider("mineboost.option.render_scale_min",
                            "The lowest scale Dynamic Render Scale may go down to.",
                            BoostConfig.RENDER_SCALES, c.renderScaleMin, v -> v + "%", v -> c.renderScaleMin = v),
                    sectionCullingOption = toggle("mineboost.option.section_culling",
                            "Section Culling: the game already knows which chunk sections you can't see (caves under you, "
                                    + "behind hills) but still draws the mobs in them. This skips those mobs.",
                            c.sectionCulling, v -> c.sectionCulling = v),
                    toggle("mineboost.option.far_entity_ticks",
                            "Experimental: mobs more than 48 blocks away and behind you are animated only every 4th tick on "
                                    + "your side. The world still runs them normally.",
                            c.farEntityTicks, v -> c.farEntityTicks = v),
                    toggle("mineboost.option.smooth_fog",
                            "Smooth Fog Edge: fog starts earlier so chunks at the edge of your render distance fade in instead "
                                    + "of popping. Good with Smart Render Distance.",
                            c.smoothFog, v -> c.smoothFog = v),
                    listSlider("mineboost.option.max_sounds",
                            "Most sounds playing at once. Huge farms and explosions can play hundreds.",
                            BoostConfig.MAX_SOUNDS, c.maxSounds, v -> v == 0 ? "No limit" : String.valueOf(v), v -> c.maxSounds = v),
                    toggle("mineboost.option.frame_graph",
                            "A small graph of the last 100 frame times under the FPS Overlay. Tall red bars are stutters.",
                            c.frameGraph, v -> c.frameGraph = v),
                    toggle("mineboost.option.spike_log",
                            "Note every frame slower than 50 ms, with what was going on (see Tools > Last report & spikes, "
                                    + "and config/minemeboost/spikes.log).",
                            c.spikeLog, v -> c.spikeLog = v),
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
                            SimpleOption.constantTooltip(withImpact("mineboost.option.fps_overlay", 
                                    "Show FPS, average FPS and 1% low in the top-right corner. "
                                            + "The 1% low tells you how bad the stutters are.")),
                            c.fpsOverlay,
                            value -> c.fpsOverlay = value),
            };
        }
    }
}
