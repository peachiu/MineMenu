package com.minemenu;

/**
 * Every on/off cheat in the menu. The menu flips these flags on the client;
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
    // Combat
    ONE_HIT_KILL(Category.COMBAT, "One-Hit Kill", "Every hit you land does massive damage."),
    MOB_AURA(Category.COMBAT, "Mob Aura", "Hostile mobs that come within 8 blocks of you die instantly."),
    MEGA_KNOCKBACK(Category.COMBAT, "Mega Knockback", "Every mob you hit goes flying."),
    THOR_MODE(Category.COMBAT, "Thor Mode", "Every mob you hit is struck by lightning (8 extra damage and fire; it never hurts you or burns the area)."),
    EXPLOSIVE_ARROWS(Category.COMBAT, "Explosive Arrows", "Your arrows explode like a creeper when they hit something. They break blocks!"),
    STUN_HITS(Category.COMBAT, "Stun Hits", "Every mob you hit freezes in place for 10 seconds."),
    MOB_GLOW(Category.COMBAT, "Mob Glow", "Every mob within 64 blocks glows, so you can see it through walls."),
    REVENGE(Category.COMBAT, "Revenge Mode", "If a mob kills you, lightning destroys it and you respawn right where you died."),
    // World
    FREEZE_WORLD(Category.WORLD, "Freeze World", "Freeze every mob, projectile and machine. You can still move."),
    ALWAYS_DAY(Category.WORLD, "Always Day", "Stops time at midday and keeps the weather clear."),
    ORE_VISION(Category.WORLD, "Ore Vision", "Diamonds, gold, iron, emeralds, redstone, lapis and ancient debris within 20 blocks glow through walls."),
    VEIN_MINER(Category.WORLD, "Vein Miner", "Break one log or ore and the whole tree or vein comes down. Sneak to break just one block."),
    INSTA_MINE(Category.WORLD, "Insta-Mine", "Break blocks almost instantly."),
    AUTO_REPLANT(Category.WORLD, "Auto Replant", "Harvest a fully grown crop and it plants itself again (wheat, carrots, potatoes, beetroot, nether wart)."),
    PEACE_MODE(Category.WORLD, "Peace Mode", "No new mobs spawn (the doMobSpawning game rule, restored when you turn it off)."),
    AUTO_SMELT(Category.WORLD, "Auto Smelt", "Blocks you mine drop already smelted: iron, gold and copper ingots, glass, stone and more. Works with Vein Miner."),
    // Items
    INFINITE_ITEMS(Category.ITEMS, "Infinite Items", "The stack in your hands never runs out and your tools never break."),
    KEEP_INVENTORY(Category.ITEMS, "Keep Inventory", "Keep your items and XP when you die.");

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

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
