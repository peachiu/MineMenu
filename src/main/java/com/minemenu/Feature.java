package com.minemenu;

/**
 * Every on/off cheat in the menu. The menu flips these flags on the client;
 * {@link ServerCheats} reads them every tick on the singleplayer (integrated)
 * server and applies them to the host player.
 */
public enum Feature {
    FLY("Fly", "Fly like in creative mode. Double-tap jump to take off."),
    GOD_MODE("God Mode", "Take no damage, never get hungry, never drown or burn."),
    ONE_HIT_KILL("One-Hit Kill", "Every hit you land does massive damage."),
    SPEED("Speed", "Run and fly 3x faster."),
    SUPER_JUMP("Super Jump", "Jump really high. Fall damage is turned off while this is on."),
    NIGHT_VISION("Night Vision", "See clearly in the dark and underwater."),
    INSTA_MINE("Insta-Mine", "Break blocks almost instantly."),
    LONG_REACH("Long Reach", "Reach blocks and mobs from much farther away."),
    MOB_AURA("Mob Aura", "Hostile mobs that come within 8 blocks of you die instantly."),
    FREEZE_WORLD("Freeze World", "Freeze every mob, projectile and machine. You can still move."),
    REVENGE("Revenge Mode", "If a mob kills you, lightning destroys it and you respawn right where you died.");

    private final String displayName;
    private final String description;

    Feature(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
