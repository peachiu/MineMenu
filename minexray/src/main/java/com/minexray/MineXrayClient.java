package com.minexray;

import com.minexray.compat.SodiumCompat;
import com.minexray.hud.SessionStats;
import com.minexray.hud.XrayHud;
import com.minexray.mode.ClassicXray;
import com.minexray.mode.LavaWarning;
import com.minexray.render.EspRenderer;
import com.minexray.render.OverlayRenderer;
import com.minexray.scan.Mesher;
import com.minexray.scan.ScanCache;
import com.minexray.scan.SectionScanner;
import com.minexray.scan.VeinMerger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MineXray: X-ray and ESP modes for singleplayer (and LAN worlds you host). On a multiplayer server every mode is
 * off and nothing is scanned, drawn or sent. Every mode runs inside its own Safe Mode: an error turns just that
 * mode off. With every mode off, MineXray costs nothing: no scanning, no drawing, no memory kept.
 */
public final class MineXrayClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("MineXray");
    private static final Map<String, Integer> sharedErrors = new ConcurrentHashMap<>();
    private static int ticks;
    private static int backgroundErrors;
    private static String lastDimension;
    private static boolean allowed;
    /** 1.0 normally; down to 0.5 while FPS stay below your FPS Target (Adaptive range). */
    private static double rangeFactor = 1.0;
    private static int settingsVersion;
    private static Mesher.Settings lastSettings;
    /** Scan now: a radius to cover even if no ESP mode is on (0 = no scan running). */
    private static volatile int forcedRadius;

    private MineXrayClient() {
    }

    public static void init() {
        XrayConfig.load();
        PresetManager.load();
        XrayKeys.register();
        SodiumCompat.logChoice();
        EspRenderer.registerShader();
        com.minexray.compat.XrayModels.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> guard("MineXray", () -> tick(client)));
        WorldRenderEvents.LAST.register(context -> {
            if (allowed) {
                guard("MineXray drawing", () -> {
                    EspRenderer.render(context);
                    OverlayRenderer.render(context);
                    com.minexray.render.LabelRenderer.capture(context);
                });
            }
        });
        HudRenderCallback.EVENT.register((context, counter) -> {
            if (allowed || ClassicXray.active()) {
                guard("MineXray HUD", () -> XrayHud.render(context, counter.getTickDelta(true)));
            }
        });
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> SectionScanner.chunkChanged(chunk.getPos().x, chunk.getPos().z));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> SectionScanner.chunkChanged(chunk.getPos().x, chunk.getPos().z));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            lastDimension = null;
            BlockFilter.rebuild(); // block tags are known now
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(MineXrayClient::freeEverything));
    }

    /** A block changed on the client (BlockUpdateMixin): scan its section again. */
    public static void blockChanged(net.minecraft.util.math.BlockPos pos) {
        SectionScanner.markDirty(pos);
        SessionStats.blockChanged(pos);
    }

    /** Shared code (not one mode): an error is logged, shown the first 3 times, and the game goes on. */
    private static void guard(String what, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vm) {
                throw vm;
            }
            int count = sharedErrors.merge(what, 1, Integer::sum);
            LOGGER.error("[MineXray] error in {} (#{})", what, count, t);
            if (count <= 3) {
                XrayHost.toast(XrayHost.trf("minexray.safe_mode.caught", "MineXray: an error in %s was caught", what),
                        XrayHost.tr("minexray.safe_mode.details", "Details are in logs/latest.log"));
            }
        }
    }

    /** True if MineXray may run at all right now (singleplayer or a LAN world you host). */
    public static boolean allowed() {
        return allowed;
    }

    /** A mode is in effect: switched on, allowed here, and (for Classic) supported. */
    public static boolean active(XrayMode mode) {
        if (!allowed || !XrayConfig.get().isOn(mode)) {
            return false;
        }
        return mode != XrayMode.CLASSIC || !SodiumCompat.present();
    }

    public static double rangeFactor() {
        return rangeFactor;
    }

    /** A mode's radius with Adaptive range applied. */
    public static int radius(int configured) {
        return Math.max(8, (int) Math.round(configured * rangeFactor));
    }

    public static int tracerCount() {
        return Math.max(1, (int) Math.round(XrayConfig.get().tracerCount * rangeFactor));
    }

    private static void tick(MinecraftClient client) {
        ticks++;
        XrayConfig.tick(ticks);
        boolean nowAllowed = client.world != null && client.player != null && XrayHost.singleplayer(client);
        if (nowAllowed != allowed) {
            allowed = nowAllowed;
            if (!allowed) {
                freeEverything();
            }
        }
        XrayKeys.tick(client);
        safe(XrayMode.CLASSIC, () -> ClassicXray.tick(client, active(XrayMode.CLASSIC)));
        if (!allowed) {
            return;
        }
        XrayHost.tick.run(); // MineMeBoostXray: Ore Vision / Chest ESP / Mob Glow stay in step with the modes
        dimensionCheck(client);
        if (ticks % 20 == 0) {
            adaptRange(client);
        }
        updateMeshSettings();
        XrayConfig config = XrayConfig.get();
        SectionScanner.tick(client, needs(config), config.memoryLimitMb * 1024L * 1024L);
        boolean esp = active(XrayMode.ORE_ESP) || active(XrayMode.BLOCK_ESP);
        if ((esp || forcedRadius > 0) && client.gameRenderer != null) {
            var camera = client.gameRenderer.getCamera().getPos();
            int radius = forcedRadius > 0 ? forcedRadius : radius(espRadius(config));
            VeinMerger.request(camera.x, camera.y, camera.z, radius, forcedRadius == 0, false);
        } else if (!VeinMerger.veins().isEmpty()) {
            VeinMerger.clear();
        }
        safe(XrayMode.LAVA_WARNING, () -> LavaWarning.tick(client, active(XrayMode.LAVA_WARNING)));
        SessionStats.tick(client);
        com.minexray.scan.ScanJob.tick();
    }

    /** What the scanner has to look for (null = nothing on, scan nothing). */
    private static SectionScanner.Needs needs(XrayConfig config) {
        boolean esp = (active(XrayMode.ORE_ESP) || active(XrayMode.BLOCK_ESP) || forcedRadius > 0) && BlockFilter.get().anyScanned();
        boolean fluid = active(XrayMode.FLUID_VIEW);
        boolean lava = fluid && config.fluidLava;
        boolean water = fluid && config.fluidWater;
        boolean cave = active(XrayMode.CAVE_FINDER);
        if (!esp && !lava && !water && !cave) {
            return null;
        }
        int radius = 0;
        if (esp) {
            radius = Math.max(radius, radius(espRadius(config)));
        }
        if (forcedRadius > 0) {
            radius = Math.max(radius, forcedRadius);
        }
        if (lava || water) {
            radius = Math.max(radius, radius(config.fluidRadius));
        }
        if (cave) {
            radius = Math.max(radius, radius(config.caveRadius));
        }
        return new SectionScanner.Needs(esp, lava, water, cave, config.caveMaxY, radius);
    }

    /** Radius of the highlighted blocks (Ore ESP and Block ESP share one scan). */
    public static int espRadius(XrayConfig config) {
        int radius = 0;
        if (active(XrayMode.ORE_ESP)) {
            radius = config.oreRadius;
        }
        if (active(XrayMode.BLOCK_ESP)) {
            radius = Math.max(radius, config.blockEspRadius);
        }
        return radius == 0 ? config.oreRadius : radius;
    }

    private static void updateMeshSettings() {
        XrayConfig config = XrayConfig.get();
        Mesher.Settings now = new Mesher.Settings(settingsVersion, active(XrayMode.ORE_ESP), active(XrayMode.BLOCK_ESP),
                config.espStyle(), Math.round(config.lineOpacity * 2.55f), Math.round(config.fillOpacity * 2.55f),
                !"MESH".equals(config.caveStyle), BlockFilter.get());
        if (lastSettings == null || !sameSettings(lastSettings, now)) {
            settingsVersion++;
            now = new Mesher.Settings(settingsVersion, now.ore(), now.block(), now.style(), now.lineAlpha(), now.fillAlpha(),
                    now.caveDots(), now.filter());
            lastSettings = now;
            Mesher.update(now);
        }
    }

    private static boolean sameSettings(Mesher.Settings a, Mesher.Settings b) {
        return a.ore() == b.ore() && a.block() == b.block() && a.style() == b.style() && a.lineAlpha() == b.lineAlpha()
                && a.fillAlpha() == b.fillAlpha() && a.caveDots() == b.caveDots() && a.filter() == b.filter();
    }

    /** The filter was rebuilt: rescan if what's highlighted changed, else just redraw; reload chunks for Classic. */
    static void filterChanged(BlockFilter filter, boolean visibleChanged) {
        if (filter.scanChanged) {
            SectionScanner.invalidateAll();
        }
        if (visibleChanged) {
            ClassicXray.requestReload();
        }
    }

    /** Changing dimension: clear the cache, and switch preset if Auto per dimension is on. */
    private static void dimensionCheck(MinecraftClient client) {
        String dimension = client.world.getRegistryKey().getValue().toString();
        if (dimension.equals(lastDimension)) {
            return;
        }
        boolean first = lastDimension == null;
        lastDimension = dimension;
        SectionScanner.clear();
        EspRenderer.reset();
        VeinMerger.clear();
        XrayConfig config = XrayConfig.get();
        String preset = config.dimensionPresets.get(dimension);
        if (config.autoDimension && preset != null && PresetManager.get(preset) != null && !preset.equals(config.activePreset)) {
            PresetManager.apply(preset, false);
            if (!first && client.player != null) {
                client.player.sendMessage(Text.translatable("minexray.preset.auto", preset).formatted(Formatting.AQUA), true);
            }
        }
    }

    /** Adaptive range: below your FPS Target (or 30 FPS without one), radii and tracers shrink, down to half. */
    private static void adaptRange(MinecraftClient client) {
        if (!XrayConfig.get().adaptiveRange) {
            rangeFactor = 1.0;
            return;
        }
        int target = XrayHost.fpsTarget.getAsInt();
        if (target <= 0) {
            target = 30;
        }
        int fps = client.getCurrentFps();
        if (fps < target * 0.9) {
            rangeFactor = Math.max(0.5, rangeFactor - 0.1);
        } else if (fps > target * 1.15) {
            rangeFactor = Math.min(1.0, rangeFactor + 0.05);
        }
    }

    /** Scan now: cover {@code radius} blocks even if no ESP mode is on; 0 when the scan is done. */
    public static void forceRadius(int radius) {
        forcedRadius = radius;
    }

    public static int forcedRadius() {
        return forcedRadius;
    }

    private static void freeEverything() {
        SectionScanner.clear();
        VeinMerger.clear();
        EspRenderer.reset();
        OverlayRenderer.reset();
        com.minexray.scan.ScanJob.cancel();
        forcedRadius = 0;
        lastDimension = null;
    }

    /** Runs a piece of one mode; on an error that mode is switched off (Safe Mode) and the game goes on. */
    public static void safe(XrayMode mode, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vm) {
                throw vm;
            }
            LOGGER.error("[Safe Mode] MineXray {} was disabled because of an error", mode.displayName(), t);
            XrayConfig.get().set(mode, false);
            XrayHost.toast(XrayHost.trf("minexray.safe_mode.disabled", "%s was disabled because of an error", mode.displayName()),
                    XrayHost.tr("minexray.safe_mode.details", "Details are in logs/latest.log"));
        }
    }

    /** An error on the scanner thread: logged; after three, the scanning modes are switched off. */
    public static void backgroundError(String where, Throwable t) {
        LOGGER.error("[MineXray] error in the {}", where, t);
        if (++backgroundErrors == 3) {
            MinecraftClient.getInstance().execute(() -> {
                XrayConfig config = XrayConfig.get();
                for (XrayMode mode : new XrayMode[]{XrayMode.ORE_ESP, XrayMode.BLOCK_ESP, XrayMode.CAVE_FINDER, XrayMode.FLUID_VIEW}) {
                    if (config.isOn(mode)) {
                        config.set(mode, false);
                    }
                }
                XrayHost.toast(XrayHost.trf("minexray.safe_mode.disabled", "%s was disabled because of an error", "MineXray ESP"),
                        XrayHost.tr("minexray.safe_mode.details", "Details are in logs/latest.log"));
                backgroundErrors = 0;
            });
        }
    }

    /** For profiles (MineMeBoostXray): the modes that are on, as "xray:<mode>". */
    public static java.util.List<String> profileState() {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (XrayMode mode : XrayMode.values()) {
            if (XrayConfig.get().isOn(mode)) {
                list.add("xray:" + mode.id);
            }
        }
        return list;
    }

    public static void loadProfileState(java.util.List<String> enabled) {
        if (enabled.stream().noneMatch(id -> id.startsWith("xray:"))) {
            return; // an older profile: leave MineXray as it is
        }
        for (XrayMode mode : XrayMode.values()) {
            XrayConfig.get().set(mode, enabled.contains("xray:" + mode.id));
        }
    }

    /** For tests and the Overview page. */
    public static String debugLine() {
        return ScanCache.size() + " sections, " + ScanCache.bytes() / 1024 + " KB, " + SectionScanner.pending() + " queued, "
                + VeinMerger.veins().size() + " veins, range x" + String.format(java.util.Locale.ROOT, "%.1f", rangeFactor);
    }
}
