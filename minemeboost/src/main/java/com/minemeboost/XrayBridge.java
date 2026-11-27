package com.minemeboost;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.core.FeatureSettings;
import com.minemenu.ui.MenuEntries;
import com.minemenu.ui.MenuEntry;
import com.minexray.MineXrayClient;
import com.minexray.PresetManager;
import com.minexray.XrayConfig;
import com.minexray.XrayHost;
import com.minexray.XrayKeys;
import com.minexray.XrayMode;
import com.minexray.XrayPreset;
import com.minexray.hud.XrayHud;
import com.minexray.scan.VeinMerger;
import com.minexray.screen.XrayScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Joins MineXray (its own mod too) to MineMenu and MineBoost in MineMeBoostXray: config in config/minemeboost,
 * MineMenu's themes, waypoints, Command Palette, profiles and minimap, MineBoost's FPS Target and A/B test, the
 * tab row, and Ore Vision / Chest ESP / Mob Glow as shortcuts to MineXray's modes.
 */
final class XrayBridge {
    private static final Map<Feature, Boolean> lastShortcut = new EnumMap<>(Feature.class);

    private XrayBridge() {
    }

    /** Before MineXrayClient.init(). */
    static void install() {
        XrayHost.configDir = com.minemenu.core.CheatConfig.DIR;
        XrayHost.fpsTarget = () -> com.mineboost.BoostConfig.get().fpsTarget;
        XrayHost.firstStart = XrayBridge::takeOverMineMenu;
        XrayHost.waypoints = com.minemenu.Waypoints::addAt;
        XrayHost.costTest = XrayCostTest::start;
        XrayHost.costTestRunning = com.mineboost.tools.BoostAB::running;
        XrayHost.tabs = screen -> Hub.tabButtons(Hub.Tab.MINEXRAY);
        XrayHost.tick = XrayBridge::tick;
        XrayHost.theme = new XrayHost.Theme() {
            @Override
            public void overlay(net.minecraft.client.gui.DrawContext context, int width, int height) {
                com.minemenu.ui.Theme.overlay(context, width, height);
            }

            @Override
            public void panel(net.minecraft.client.gui.DrawContext context, int x1, int y1, int x2, int y2) {
                com.minemenu.ui.Theme.panel(context, x1, y1, x2, y2);
            }

            @Override
            public int accent() {
                return com.minemenu.ui.Theme.accent();
            }
        };
        // The old MineMenu hacks become shortcuts: MineXray draws, they just switch the mode.
        com.minemenu.esp.EspModules.delegated = true;
        com.minemenu.ExtraCheats.mobGlowDelegated = true;
    }

    /** After MineXrayClient.init(). */
    static void connect() {
        registerEntries();
        com.minemenu.ui.Profiles.extraEnabled = MineXrayClient::profileState;
        com.minemenu.ui.Profiles.extraLoad = MineXrayClient::loadProfileState;
        com.minemenu.hud.Minimap.layers.add(XrayHud::minimapLayer);
    }

    /**
     * 3.6 first start: the old MineMenu hacks become shortcuts to MineXray modes. Their on/off state and radii
     * move over (the hacks themselves, and any key you gave them, keep working).
     */
    private static void takeOverMineMenu(XrayConfig config) {
        config.oreEsp = CheatState.isOn(Feature.ORE_VISION);
        config.blockEsp = CheatState.isOn(Feature.CHEST_ESP);
        config.entityEsp = CheatState.isOn(Feature.MOB_GLOW);
        int oldOre = (int) FeatureSettings.get(Feature.ORE_VISION, "radius");
        if (oldOre != 24) { // 24 was the old default; a changed value is kept
            config.oreRadius = XrayConfig.clamp(oldOre, 8, 128);
        }
        config.blockEspRadius = XrayConfig.clamp((int) FeatureSettings.get(Feature.CHEST_ESP, "radius"), 8, 128);
        LoggerFactory.getLogger("MineXray").info("[MineXray] Took over Ore Vision ({}), Chest ESP ({}) and Mob Glow ({}) from MineMenu",
                config.oreEsp, config.blockEsp, config.entityEsp);
    }

    private static void tick() {
        if (!com.minemenu.MineMenuClient.isSingleplayer(net.minecraft.client.MinecraftClient.getInstance())) {
            return; // on servers MineMenu's cheats stay off; MineXray's modes run on their own
        }
        syncShortcut(Feature.ORE_VISION, XrayMode.ORE_ESP);
        syncShortcut(Feature.CHEST_ESP, XrayMode.BLOCK_ESP);
        syncShortcut(Feature.MOB_GLOW, XrayMode.ENTITY_ESP);
    }

    /** MineMenu's Ore Vision / Chest ESP / Mob Glow and MineXray's modes stay in step, whichever you switch. */
    private static void syncShortcut(Feature feature, XrayMode mode) {
        boolean featureOn = CheatState.isOn(feature);
        Boolean last = lastShortcut.get(feature);
        XrayConfig config = XrayConfig.get();
        if (last != null && featureOn != last) {
            config.set(mode, featureOn); // switched in MineMenu
        } else if (config.isOn(mode) != featureOn) {
            CheatState.toggle(feature); // switched in MineXray (or by its key)
            featureOn = !featureOn;
        }
        lastShortcut.put(feature, featureOn);
    }

    private static void registerEntries() {
        Supplier<List<MenuEntry>> before = MenuEntries.extra;
        MenuEntries.extra = () -> {
            List<MenuEntry> list = new ArrayList<>(before.get());
            list.addAll(entries());
            return list;
        };
    }

    private static List<MenuEntry> entries() {
        List<MenuEntry> list = new ArrayList<>();
        for (XrayMode mode : XrayMode.values()) {
            list.add(new MenuEntry("xray:mode:" + mode.id, "Xray " + mode.displayName(), mode.description(), null, MenuEntry.Kind.HACK,
                    () -> XrayConfig.get().isOn(mode), (client, parent) -> XrayKeys.toggle(client, mode), XrayConfig.get().allowMultiplayer));
        }
        for (XrayPreset preset : PresetManager.all()) {
            list.add(new MenuEntry("xray:preset:" + preset.name, "Xray " + preset.name,
                    com.minexray.XrayHost.tr("minexray.entry.preset.desc", "Use this MineXray preset."), null, MenuEntry.Kind.PRESET,
                    () -> preset.name.equals(XrayConfig.get().activePreset), (client, parent) -> {
                        PresetManager.apply(preset.name, true);
                        XrayKeys.message(client, Text.translatable("minexray.preset.now", preset.name).formatted(Formatting.AQUA));
                    }, XrayConfig.get().allowMultiplayer));
        }
        list.add(new MenuEntry("xray:scan", com.minexray.XrayHost.tr("minexray.entry.scan", "Xray Scan now"),
                com.minexray.XrayHost.tr("minexray.entry.scan.desc", "List every vein around you."), null, MenuEntry.Kind.SCREEN, null,
                (client, parent) -> client.setScreen(new XrayScreen(parent, XrayScreen.Page.SCANNER, true)), XrayConfig.get().allowMultiplayer));
        list.add(new MenuEntry("xray:open", com.minexray.XrayHost.tr("minexray.entry.open", "Xray: open MineXray"),
                com.minexray.XrayHost.tr("minexray.entry.open.desc", "X-ray and ESP modes, block list, presets and scanner."), null,
                MenuEntry.Kind.SCREEN, null, (client, parent) -> client.setScreen(new XrayScreen(parent, XrayScreen.Page.OVERVIEW, false)), true));
        list.add(new MenuEntry("xray:mine_nearest", com.minexray.XrayHost.tr("minexray.entry.mine_nearest", "Xray Mine nearest vein"),
                com.minexray.XrayHost.tr("minexray.entry.mine_nearest.desc", "Mines the whole nearest highlighted vein (Vein Miner)."), null,
                MenuEntry.Kind.ACTION, null, (client, parent) -> {
                    List<VeinMerger.Vein> veins = VeinMerger.veins();
                    if (!veins.isEmpty()) {
                        com.minexray.XrayActions.mine(veins.get(0));
                    }
                }, false));
        return list;
    }}
