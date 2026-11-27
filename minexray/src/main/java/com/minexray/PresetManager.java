package com.minexray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The presets: nine built-in ones (written to disk on first start, editable, and restorable), plus your own.
 * Save, rename, duplicate, delete, and share as a text code ("MXR1:" + base64 of the JSON).
 */
public final class PresetManager {
    public static Path dir() {
        return XrayHost.configDir.resolve("xray-presets");
    }
    private static final String SHARE_PREFIX = "MXR1:";
    private static final Logger LOGGER = LoggerFactory.getLogger("MineXray");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, XrayPreset> presets = new LinkedHashMap<>();

    // Ore colours (bright enough to read through dark stone).
    static final int COAL = 0x606060, IRON = 0xD8AF93, COPPER = 0xE77C56, GOLD = 0xFCEE4B, REDSTONE = 0xFF2828,
            LAPIS = 0x2E5BFF, DIAMOND = 0x5DECF5, EMERALD = 0x17DD62, NETHER_GOLD = 0xFFB52E, QUARTZ = 0xF2EBE3,
            DEBRIS = 0xD2693C, LAVA = 0xFF7A00;

    private PresetManager() {
    }

    public static void load() {
        presets.clear();
        for (XrayPreset preset : builtIns()) {
            presets.put(preset.name, preset);
        }
        try {
            Files.createDirectories(dir());
            try (Stream<Path> files = Files.list(dir())) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                    try {
                        XrayPreset preset = GSON.fromJson(Files.readString(file), XrayPreset.class);
                        if (preset != null && preset.name != null && preset.blocks != null) {
                            presets.put(preset.name, preset);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("[MineXray] Skipped unreadable preset {}", file, e);
                    }
                }
            }
            for (XrayPreset preset : builtIns()) {
                if (!Files.exists(file(preset.name))) {
                    save(presets.get(preset.name));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[MineXray] Couldn't read {}", dir(), e);
        }
    }

    public static List<XrayPreset> all() {
        return new ArrayList<>(presets.values());
    }

    public static XrayPreset get(String name) {
        return name == null ? null : presets.get(name);
    }

    /** The preset in use (falls back to All Ores if it was deleted). */
    public static XrayPreset active() {
        XrayPreset preset = presets.get(XrayConfig.get().activePreset);
        if (preset == null) {
            preset = presets.get("All Ores");
        }
        if (preset == null) {
            preset = builtIns().get(0);
            presets.put(preset.name, preset);
        }
        return preset;
    }

    /** Picks a preset. {@code byHand}: also switches on the modes and categories it asks for. */
    public static void apply(String name, boolean byHand) {
        XrayPreset preset = presets.get(name);
        if (preset == null) {
            return;
        }
        XrayConfig config = XrayConfig.get();
        config.activePreset = name;
        if (byHand) {
            for (Map.Entry<String, Boolean> entry : preset.modes.entrySet()) {
                for (XrayMode mode : XrayMode.values()) {
                    if (mode.id.equals(entry.getKey())) {
                        config.set(mode, entry.getValue());
                    }
                }
            }
            for (String category : preset.categories) {
                config.categories.put(category, true);
            }
        }
        XrayConfig.markDirty();
        BlockFilter.rebuild();
    }

    /** The next preset in the list (key: Next preset). */
    public static String next() {
        List<String> names = new ArrayList<>(presets.keySet());
        int i = names.indexOf(active().name);
        String name = names.get((i + 1) % names.size());
        apply(name, false);
        return name;
    }

    public static void save(XrayPreset preset) {
        if (preset == null) {
            return;
        }
        presets.put(preset.name, preset);
        try {
            Files.createDirectories(dir());
            Files.writeString(file(preset.name), GSON.toJson(preset));
        } catch (Exception e) {
            LOGGER.warn("[MineXray] Couldn't save preset {}", preset.name, e);
        }
    }

    /** Call after changing the active preset's blocks: saves it and rebuilds the filter. */
    public static void changed(XrayPreset preset) {
        save(preset);
        if (preset == active()) {
            BlockFilter.rebuild();
        }
    }

    public static boolean rename(String oldName, String typedName) {
        String newName = clean(typedName);
        XrayPreset preset = presets.get(oldName);
        if (preset == null || newName.isEmpty() || presets.containsKey(newName)) {
            return false;
        }
        presets.remove(oldName);
        delete(file(oldName));
        preset.name = newName;
        preset.builtIn = false;
        save(preset);
        XrayConfig config = XrayConfig.get();
        if (oldName.equals(config.activePreset)) {
            config.activePreset = newName;
        }
        config.dimensionPresets.replaceAll((dim, p) -> p.equals(oldName) ? newName : p);
        XrayConfig.markDirty();
        return true;
    }

    public static String duplicate(String name) {
        XrayPreset preset = presets.get(name);
        if (preset == null) {
            return null;
        }
        String copyName = freeName(name + " (copy)");
        save(preset.copy(copyName));
        return copyName;
    }

    public static void delete(String name) {
        if (presets.size() <= 1 || presets.remove(name) == null) {
            return;
        }
        delete(file(name));
        if (name.equals(XrayConfig.get().activePreset)) {
            apply(presets.keySet().iterator().next(), false);
        }
    }

    /** Puts the nine built-in presets back as they came (your own presets are kept). */
    public static void restoreBuiltIns() {
        for (XrayPreset preset : builtIns()) {
            save(preset);
        }
        BlockFilter.rebuild();
    }

    public static String export(String name) {
        XrayPreset preset = presets.get(name);
        if (preset == null) {
            return "";
        }
        return SHARE_PREFIX + Base64.getEncoder().encodeToString(new Gson().toJson(preset).getBytes(StandardCharsets.UTF_8));
    }

    /** Adds a preset from a share code; returns its (possibly renamed) name, or null if the code is broken. */
    public static String importCode(String code) {
        try {
            String text = code.trim();
            if (text.startsWith(SHARE_PREFIX)) {
                text = text.substring(SHARE_PREFIX.length());
            }
            XrayPreset preset = GSON.fromJson(new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8), XrayPreset.class);
            if (preset == null || preset.blocks == null) {
                return null;
            }
            preset.name = freeName(clean(preset.name == null ? "Imported" : preset.name));
            preset.builtIn = false;
            preset.blocks.removeIf(rule -> rule == null || rule.id == null);
            save(preset);
            return preset.name;
        } catch (Exception e) {
            return null;
        }
    }

    public static String freeName(String base) {
        String name = clean(base);
        if (name.isEmpty()) {
            name = "Preset";
        }
        String candidate = name;
        for (int i = 2; presets.containsKey(candidate); i++) {
            candidate = name + " " + i;
        }
        return candidate;
    }

    private static String clean(String name) {
        return name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    private static Path file(String name) {
        return dir().resolve(clean(name).toLowerCase(Locale.ROOT).replace(' ', '_') + ".json");
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // The built-in presets
    // ------------------------------------------------------------------

    private static XrayPreset ores(String name, Object... idColor) {
        XrayPreset preset = new XrayPreset(name, true);
        for (int i = 0; i < idColor.length; i += 2) {
            preset.add(BlockRule.ore("minecraft:" + idColor[i], (Integer) idColor[i + 1]));
        }
        return preset;
    }

    private static BlockRule onlyClassic(String id) {
        return new BlockRule("minecraft:" + id, -1, EspCategory.OTHER.name(), true, false);
    }

    private static BlockRule block(String id, EspCategory category) {
        return new BlockRule("minecraft:" + id, -1, category.name(), true, true);
    }

    public static List<XrayPreset> builtIns() {
        List<XrayPreset> list = new ArrayList<>();
        list.add(ores("All Ores", "coal_ore", COAL, "iron_ore", IRON, "copper_ore", COPPER, "gold_ore", GOLD,
                "redstone_ore", REDSTONE, "lapis_ore", LAPIS, "diamond_ore", DIAMOND, "emerald_ore", EMERALD,
                "nether_gold_ore", NETHER_GOLD, "nether_quartz_ore", QUARTZ, "ancient_debris", DEBRIS)
                .add(onlyClassic("lava")).add(onlyClassic("water")));
        list.add(ores("Diamonds Only", "diamond_ore", DIAMOND).add(onlyClassic("lava")));
        list.add(ores("Netherite Hunt", "ancient_debris", DEBRIS, "nether_quartz_ore", QUARTZ, "gold_ore", GOLD,
                "nether_gold_ore", NETHER_GOLD, "gilded_blackstone", NETHER_GOLD).add(onlyClassic("lava")));
        list.add(ores("Gold & Emerald", "gold_ore", GOLD, "nether_gold_ore", NETHER_GOLD, "emerald_ore", EMERALD).add(onlyClassic("lava")));
        list.add(ores("Redstone & Lapis", "redstone_ore", REDSTONE, "lapis_ore", LAPIS).add(onlyClassic("lava")));
        list.add(ores("Caves", "diamond_ore", DIAMOND, "emerald_ore", EMERALD).add(onlyClassic("lava"))
                .mode(XrayMode.CAVE_FINDER, true).mode(XrayMode.FLUID_VIEW, true));
        list.add(new XrayPreset("Structures", true).add(block("spawner", EspCategory.SPAWNER)).add(block("trial_spawner", EspCategory.SPAWNER))
                .add(block("vault", EspCategory.VAULT)).add(block("chest", EspCategory.STORAGE)).add(block("barrel", EspCategory.STORAGE))
                .add(block("end_portal_frame", EspCategory.PORTAL)).add(block("suspicious_sand", EspCategory.SUSPICIOUS))
                .add(block("suspicious_gravel", EspCategory.SUSPICIOUS)).add(block("mossy_cobblestone", EspCategory.OTHER))
                .mode(XrayMode.BLOCK_ESP, true).category(EspCategory.STORAGE).category(EspCategory.SPAWNER)
                .category(EspCategory.VAULT).category(EspCategory.PORTAL).category(EspCategory.SUSPICIOUS));
        list.add(new XrayPreset("Ancient City", true).add(block("sculk_shrieker", EspCategory.SCULK)).add(block("sculk_sensor", EspCategory.SCULK))
                .add(block("calibrated_sculk_sensor", EspCategory.SCULK)).add(new BlockRule("minecraft:sculk_catalyst", 0x2ED1C4, EspCategory.OTHER.name(), true, true))
                .add(new BlockRule("minecraft:reinforced_deepslate", 0x7A8A8A, EspCategory.OTHER.name(), true, true))
                .add(block("chest", EspCategory.STORAGE)).mode(XrayMode.BLOCK_ESP, true).category(EspCategory.SCULK).category(EspCategory.STORAGE));
        XrayPreset clean = ores("Clean Classic", "diamond_ore", DIAMOND, "emerald_ore", EMERALD, "gold_ore", GOLD, "iron_ore", IRON,
                "ancient_debris", DEBRIS);
        for (BlockRule rule : clean.blocks) {
            rule.esp = false;
        }
        clean.add(onlyClassic("lava")).add(onlyClassic("chest")).add(onlyClassic("spawner")).mode(XrayMode.CLASSIC, true);
        list.add(clean);
        return list;
    }
}
