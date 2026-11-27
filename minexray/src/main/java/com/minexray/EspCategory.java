package com.minexray;


import java.util.List;

/**
 * What a highlighted block is. Ores (and anything not in a Block ESP category) belong to Ore ESP; the rest to
 * Block ESP, where each category has its own switch and colour. The block lists can hold tags ("#...").
 */
public enum EspCategory {
    ORE("Ores", 0xFFFFFF, true, List.of()),
    STORAGE("Storage", 0xFFAA00, true, List.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
            "minecraft:ender_chest", "#minecraft:shulker_boxes")),
    SPAWNER("Spawners", 0xFF3355, true, List.of("minecraft:spawner", "minecraft:trial_spawner")),
    VAULT("Vaults", 0xC080FF, true, List.of("minecraft:vault")),
    PORTAL("Portals", 0xAA44FF, true, List.of("minecraft:end_portal_frame", "minecraft:nether_portal",
            "minecraft:end_portal", "minecraft:end_gateway")),
    AMETHYST("Amethyst", 0xB57EDC, true, List.of("minecraft:budding_amethyst")),
    SUSPICIOUS("Suspicious sand/gravel", 0xE8C878, true, List.of("minecraft:suspicious_sand", "minecraft:suspicious_gravel")),
    SCULK("Sculk sensors/shriekers", 0x1FA7B5, true, List.of("minecraft:sculk_sensor", "minecraft:calibrated_sculk_sensor",
            "minecraft:sculk_shrieker")),
    REDSTONE("Redstone", 0xFF4040, false, List.of("minecraft:redstone_wire", "minecraft:repeater", "minecraft:comparator",
            "minecraft:piston", "minecraft:sticky_piston", "minecraft:observer", "minecraft:dispenser", "minecraft:dropper",
            "minecraft:hopper", "minecraft:redstone_torch", "minecraft:lever", "minecraft:tripwire_hook",
            "minecraft:daylight_detector", "minecraft:target", "minecraft:redstone_lamp")),
    INFESTED("Infested blocks", 0x9AE07A, true, List.of("minecraft:infested_stone", "minecraft:infested_cobblestone",
            "minecraft:infested_stone_bricks", "minecraft:infested_mossy_stone_bricks", "minecraft:infested_cracked_stone_bricks",
            "minecraft:infested_chiseled_stone_bricks", "minecraft:infested_deepslate")),
    OTHER("Other", 0xFFFFFF, true, List.of());

    private final String english;
    public final int defaultColor;
    public final boolean defaultOn;
    /** Blocks Block ESP shows for this category even if the preset doesn't list them. */
    public final List<String> defaultBlocks;

    EspCategory(String english, int defaultColor, boolean defaultOn, List<String> defaultBlocks) {
        this.english = english;
        this.defaultColor = defaultColor;
        this.defaultOn = defaultOn;
        this.defaultBlocks = defaultBlocks;
    }

    /** Drawn by Ore ESP (true) or Block ESP (false). */
    public boolean oreLayer() {
        return this == ORE || this == OTHER;
    }

    public String displayName() {
        return XrayHost.tr("minexray.category." + name().toLowerCase(java.util.Locale.ROOT), english);
    }

    public static EspCategory parse(String name) {
        try {
            return valueOf(name);
        } catch (Exception e) {
            return ORE;
        }
    }
}
