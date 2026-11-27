package com.minemenu;

/**
 * Every on/off cheat in the menu (3.4: also the Build, Fun and Utility pages). The menu flips these flags on the client;
 * {@link ServerCheats} reads them every tick on the singleplayer (integrated)
 * server and applies them to the host player.
 */
public enum Feature {
    // Player
    FLY(Category.PLAYER, "Fly", "Fly like in creative mode. Double-tap jump to take off."),
    GOD_MODE(Category.PLAYER, "God Mode", "Take no damage, never get hungry, never drown or burn."),
    SPEED(Category.PLAYER, "Speed", "Run and fly 3x faster."),
    SUPER_JUMP(Category.PLAYER, "Super Jump", "Jump really high. Fall damage is turned off while this is on."),
    NIGHT_VISION(Category.PLAYER, "Night Vision", "See clearly in the dark and underwater."),
    LONG_REACH(Category.PLAYER, "Long Reach", "Reach blocks and mobs from much farther away."),
    ITEM_MAGNET(Category.PLAYER, "Item Magnet", "Dropped items and XP orbs within 12 blocks fly to you."),
    WATER_WALK(Category.PLAYER, "Water Walk", "Walk on water and lava as if they were solid ground. Sneak to sink in."),
    SPIDER_CLIMB(Category.PLAYER, "Spider Climb", "Walk into a wall to climb straight up it, like a spider."),
    NO_FALL_DAMAGE(Category.PLAYER, "No Fall Damage", "Falling never hurts, from any height."),
    JETPACK(Category.PLAYER, "Jetpack", "Hold jump to fly upwards, no Fly needed. You never take fall damage while it's on."),
    FEATHER_FALL(Category.PLAYER, "Feather Fall", "Fall slowly and gently, like a feather (Slow Falling)."),
    NOCLIP(Category.PLAYER, "Noclip", "Fly through walls and the ground (spectator mode). Turn it off to get your normal game mode back where you are."),
    ZOOM(Category.PLAYER, "Zoom", "Hold Z to zoom in like a spyglass. Scroll while holding to zoom more or less. (Change the key in Controls.)"),
    STEP_UP(Category.PLAYER, "Step-Up", "Walk up full blocks without jumping, like stairs."),
    FAST_SWIM(Category.PLAYER, "Fast Swim", "Swim and walk in water as fast as on land (Dolphin's Grace)."),
    ELYTRA_BOOST(Category.PLAYER, "Elytra Boost", "While gliding with an elytra, hold forward to speed up like with endless fireworks."),
    AUTO_EAT(Category.PLAYER, "Auto-Eat", "When you get hungry, you eat the best food in your inventory by yourself. Never eats golden apples or bad food."),
    AUTO_TOOL(Category.PLAYER, "Auto-Tool", "When you start breaking a block, your hotbar switches to the best tool for it."),
    FREECAM(Category.PLAYER, "Freecam", "Fly the camera around freely while your body stays where it is. WASD, space and sneak move the camera."),
    FULL_BRIGHT(Category.PLAYER, "Full Bright", "Everything is fully lit, like max gamma: no dark caves or nights, without the Night Vision effect."),
    AUTO_SPRINT(Category.PLAYER, "Auto-Sprint", "You always sprint when walking forward."),
    DOUBLE_JUMP(Category.PLAYER, "Double Jump", "Jump again in mid-air (1 to 3 extra jumps, right-click the button to change)."),
    DASH(Category.PLAYER, "Dash", "Press the Dash key (V) for a quick burst in the direction you're moving. Has a short cooldown."),
    SCAFFOLD(Category.PLAYER, "Scaffold", "Places blocks from your hotbar under your feet when you walk off an edge."),
    LAVA_SWIM(Category.PLAYER, "Lava Swim", "Swim in lava like in water: no burning, fast movement and a clear view."),
    GRAVITY_CONTROL(Category.PLAYER, "Gravity Control", "Change your own gravity from 0.1x (moon) to 3x (right-click the button for the slider)."),
    MORPH(Category.PLAYER, "Morph", "Look like a mob and get its powers (pick it with right-click). R uses the power, U turns you back."),
    PHASE(Category.PLAYER, "Phase", "Walk through doors, trapdoors, fence gates, glass, panes and iron bars without Noclip."),
    // Combat
    ONE_HIT_KILL(Category.COMBAT, "One-Hit Kill", "Every hit you land does massive damage."),
    MOB_AURA(Category.COMBAT, "Mob Aura", "Hostile mobs that come within 8 blocks of you die instantly."),
    MEGA_KNOCKBACK(Category.COMBAT, "Mega Knockback", "Every mob you hit goes flying."),
    THOR_MODE(Category.COMBAT, "Thor Mode", "Every mob you hit is struck by lightning (8 extra damage and fire; it never hurts you or burns the area)."),
    EXPLOSIVE_ARROWS(Category.COMBAT, "Explosive Arrows", "Your arrows explode like a creeper when they hit something. They break blocks!"),
    STUN_HITS(Category.COMBAT, "Stun Hits", "Every mob you hit freezes in place for 10 seconds."),
    MOB_GLOW(Category.COMBAT, "Mob Glow", "Every mob within 64 blocks glows, so you can see it through walls."),
    REVENGE(Category.COMBAT, "Revenge Mode", "If a mob kills you, lightning destroys it and you respawn right where you died."),
    AUTO_TOTEM(Category.COMBAT, "Auto-Totem", "Keeps a Totem of Undying in your off hand: when one is used up, the next one from your inventory takes its place."),
    TAME_ANYTHING(Category.COMBAT, "Tame Anything", "Right-click any mob with an empty hand to make it your pet: it follows you, never attacks you, and attacks what you attack."),
    ALWAYS_CRIT(Category.COMBAT, "Always Crit", "Every fully charged hit is a critical hit (+50% damage), no jumping needed."),
    BOW_AIMBOT(Category.COMBAT, "Bow Aimbot", "Your arrows curve towards the hostile mob closest to where you aimed."),
    LIFESTEAL(Category.COMBAT, "Lifesteal", "Heal a part of the damage you deal (25% by default, right-click to change)."),
    FORCE_FIELD(Category.COMBAT, "Force Field", "Hostile mobs get pushed out of a bubble around you (5 blocks by default)."),
    THORNS_AURA(Category.COMBAT, "Thorns Aura", "Mobs that hit you take twice that damage back."),
    MIND_CONTROL(Category.COMBAT, "Mind Control", "Look at a mob and press N: for 60 seconds it fights other hostile mobs for you. Works on bosses too."),
    TRIDENT_STORM(Category.COMBAT, "Trident Storm", "A thrown trident calls lightning and a rain of tridents where it lands."),
    // World
    FREEZE_WORLD(Category.WORLD, "Freeze World", "Freeze every mob, projectile and machine. You can still move."),
    ALWAYS_DAY(Category.WORLD, "Always Day", "Stops time at midday and keeps the weather clear."),
    ORE_VISION(Category.WORLD, "Ore Vision", "Diamonds, gold, iron, emeralds, redstone, lapis and ancient debris within 20 blocks glow through walls."),
    VEIN_MINER(Category.WORLD, "Vein Miner", "Break one log or ore and the whole tree or vein comes down. Sneak to break just one block."),
    INSTA_MINE(Category.WORLD, "Insta-Mine", "Break blocks almost instantly."),
    AUTO_REPLANT(Category.WORLD, "Auto Replant", "Harvest a fully grown crop and it plants itself again (wheat, carrots, potatoes, beetroot, nether wart)."),
    PEACE_MODE(Category.WORLD, "Peace Mode", "No new mobs spawn (the doMobSpawning game rule, restored when you turn it off)."),
    CHEST_ESP(Category.WORLD, "Chest ESP", "Chests, barrels, shulker boxes and ender chests within 32 blocks glow through walls."),
    GROWTH_AURA(Category.WORLD, "Growth Aura", "Crops, saplings and plants around you (8 blocks) keep growing much faster, like bone meal every second."),
    NUKER(Category.WORLD, "Nuker", "Breaks every block around you (radius 3 by default), except the floor under your feet. Sneak to pause."),
    FAST_BREED(Category.WORLD, "Fast Breed", "Animals around you (16 blocks) can breed again right away, and babies grow up instantly."),
    AUTO_SMELT(Category.WORLD, "Auto Smelt", "Blocks you mine drop already smelted: iron, gold and copper ingots, glass, stone and more. Works with Vein Miner."),
    // Items
    INFINITE_ITEMS(Category.ITEMS, "Infinite Items", "The stack in your hands never runs out and your tools never break."),
    KEEP_INVENTORY(Category.ITEMS, "Keep Inventory", "Keep your items and XP when you die."),
    INFINITE_DURABILITY(Category.ITEMS, "Infinite Durability", "Your tools, weapons and armor never wear out (stacks still get used up, unlike Infinite Items)."),
    AUTO_FISH(Category.ITEMS, "Auto-Fish", "Cast a fishing rod: it reels in by itself when a fish bites, then casts again."),
    INSTANT_BUILD(Category.ITEMS, "Instant Build", "No delay between placing blocks: hold right-click to place 20 blocks a second."),
    MAGNET_FILTER(Category.ITEMS, "Magnet Filter", "Item Magnet only pulls the items on your filter list (Magnet Filter... button), or everything but them."),
    DEATH_CHEST(Category.ITEMS, "Death Chest", "When you die without Keep Inventory, your items go into a chest where you died, with a waypoint to it."),
    // Build (3.4)
    SELECTION_WAND(Category.BUILD, "Selection Wand", "Holding a wooden axe: left-click a block for corner 1, right-click for corner 2. Then copy, fill, clear..."),
    TERRAIN_BRUSH(Category.BUILD, "Terrain Brush", "Right-click with a golden shovel to sculpt the ground where you look (Brush... button picks the tool)."),
    GRAVITY_GUN(Category.BUILD, "Gravity Gun", "Sneak + right-click with an empty hand to grab a mob or block, again to throw it."),
    // Fun (3.4)
    CHAOS_MODE(Category.FUN, "Chaos Mode", "Something random happens every few minutes: meteors, diamond rain, a mob invasion... (Chaos Events... picks which)."),
    LUCKY_BLOCKS(Category.FUN, "Lucky Blocks", "Breaking a sponge gives a random surprise, good or bad."),
    FLOOR_IS_LAVA(Category.FUN, "Floor is Lava", "Challenge: the blocks you stand on turn into magma. Keep moving!"),
    RANDOM_DROPS(Category.FUN, "Random Drops", "Challenge: every block you break drops a random item."),
    MOB_SWAP(Category.FUN, "Mob Swap", "Challenge: every 5 minutes you swap places with a random mob near you."),
    // Utility (3.4)
    MINIMAP(Category.UTILITY, "Minimap", "A map in the corner with your waypoints and nearby mobs. M opens the big map."),
    INFO_HUD(Category.UTILITY, "Info HUD", "Coordinates, direction, biome, time, day, speed, chunk and light level on screen."),
    LIGHT_OVERLAY(Category.UTILITY, "Light Level Overlay", "Marks the blocks around you (16 blocks) where mobs can spawn, in the dark."),
    CHUNK_BORDERS(Category.UTILITY, "Chunk Borders", "Shows the borders of chunks (like F3+G)."),
    SLIME_CHUNKS(Category.UTILITY, "Slime Chunks", "Shows which chunks around you are slime chunks (green)."),
    MOB_HEALTH_BARS(Category.UTILITY, "Mob Health Bars", "Shows the health of mobs above their heads.");

    private final Category category;
    private final String displayName;
    private final String description;

    Feature(Category category, String displayName, String description) {
        this.category = category;
        this.displayName = displayName;
        this.description = description;
    }

    public Category category() {
        return category;
    }

    /** In the player's language (minemenu.feature.NAME in the lang files), English if there's no translation. */
    public String displayName() {
        return com.minemenu.core.Tr.get("minemenu.feature." + name().toLowerCase(java.util.Locale.ROOT), displayName);
    }

    public String description() {
        return com.minemenu.core.Tr.get("minemenu.feature." + name().toLowerCase(java.util.Locale.ROOT) + ".desc", description);
    }

    /**
     * Whether Enable All turns this on. Noclip isn't: it puts you in spectator mode, where you can't
     * break, place or pick up anything, so it would make every other hack useless.
     */
    public boolean inEnableAll() {
        return switch (this) {
            // Spectator mode, a detached camera or a new body would make every other hack useless or confusing;
            // the challenges and the Nuker would wreck the world.
            case NOCLIP, FREECAM, MORPH, NUKER, FLOOR_IS_LAVA, RANDOM_DROPS, MOB_SWAP, CHAOS_MODE, LUCKY_BLOCKS,
                 TERRAIN_BRUSH, GRAVITY_GUN, SELECTION_WAND, SCAFFOLD -> false;
            default -> true;
        };
    }

    /** Whether this is still allowed on a multiplayer server (only things that give no unfair advantage). */
    public boolean allowedInMultiplayer() {
        // 5.0: also the ones that only change what you see on your own screen.
        return this == INFO_HUD || this == MINIMAP || this == CHUNK_BORDERS || this == ZOOM || this == AUTO_SPRINT
                || this == LIGHT_OVERLAY || this == FULL_BRIGHT || this == MOB_HEALTH_BARS;
    }

    /** Remembered between game sessions (not the ones that would be surprising when you start the game). */
    public boolean persists() {
        return this != FREECAM && this != MORPH && this != NOCLIP;
    }
}
