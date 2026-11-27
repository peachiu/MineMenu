package com.minemenu.op;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.MineMenuClient;
import com.minemenu.ServerCheats;
import com.minemenu.Waypoints;
import com.minemenu.ui.MenuEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 5.0: MineMenu on servers where you're an operator (OP, permission level 2 or more): hacks and actions are done
 * with Minecraft's own commands (/effect, /gamemode, /attribute, /give, /summon, /time, /weather, /tp...), which
 * the server allows because you have the rights. Nothing is faked: on a server where you aren't OP these stay off.
 */
public final class OpCommands {
    /** On and off commands of each hack that has a command version. */
    private record Toggle(List<String> on, List<String> off) {
    }

    private static final Map<Feature, Toggle> TOGGLES = new EnumMap<>(Feature.class);
    private static final Set<String> SCREENS = Set.of("screen:give_item", "screen:spawn_mob", "screen:give_xp", "screen:time_weather");
    private static final String[] HOSTILE = {"zombie", "husk", "drowned", "zombie_villager", "skeleton", "stray", "bogged", "wither_skeleton",
            "creeper", "spider", "cave_spider", "enderman", "witch", "slime", "magma_cube", "phantom", "blaze", "ghast", "pillager", "vindicator",
            "evoker", "vex", "ravager", "guardian", "elder_guardian", "silverfish", "endermite", "piglin_brute", "hoglin", "zoglin", "breeze",
            "shulker", "warden"};

    static {
        effect(Feature.SPEED, "speed", 2);
        effect(Feature.SUPER_JUMP, "jump_boost", 2);
        effect(Feature.NIGHT_VISION, "night_vision", 0);
        effect(Feature.FAST_SWIM, "dolphins_grace", 0);
        effect(Feature.LAVA_SWIM, "fire_resistance", 0);
        effect(Feature.INSTA_MINE, "haste", 9);
        TOGGLES.put(Feature.GOD_MODE, new Toggle(
                List.of(give("resistance", 4), give("fire_resistance", 0), give("water_breathing", 0), give("saturation", 0), give("regeneration", 4)),
                List.of(clear("resistance"), clear("fire_resistance"), clear("water_breathing"), clear("saturation"), clear("regeneration"))));
        TOGGLES.put(Feature.FLY, new Toggle(List.of("gamemode creative"), List.of("gamemode survival")));
        attribute(Feature.NO_FALL_DAMAGE, "generic.fall_damage_multiplier", "0", "1");
        attribute(Feature.FEATHER_FALL, "generic.fall_damage_multiplier", "0", "1");
        attribute(Feature.STEP_UP, "generic.step_height", "1.25", "0.6");
        attribute(Feature.GRAVITY_CONTROL, "generic.gravity", "0.03", "0.08");
        TOGGLES.put(Feature.LONG_REACH, new Toggle(
                List.of("attribute @s minecraft:player.block_interaction_range base set 10", "attribute @s minecraft:player.entity_interaction_range base set 8"),
                List.of("attribute @s minecraft:player.block_interaction_range base set 4.5", "attribute @s minecraft:player.entity_interaction_range base set 3")));
        TOGGLES.put(Feature.ALWAYS_DAY, new Toggle(List.of("time set day", "gamerule doDaylightCycle false"), List.of("gamerule doDaylightCycle true")));
        TOGGLES.put(Feature.KEEP_INVENTORY, new Toggle(List.of("gamerule keepInventory true"), List.of("gamerule keepInventory false")));
        TOGGLES.put(Feature.PEACE_MODE, new Toggle(List.of("difficulty peaceful"), List.of("difficulty normal")));
        TOGGLES.put(Feature.FREEZE_WORLD, new Toggle(List.of("tick freeze"), List.of("tick unfreeze")));
    }

    private OpCommands() {
    }

    private static String give(String effect, int amplifier) {
        return "effect give @s minecraft:" + effect + " infinite " + amplifier + " true";
    }

    private static String clear(String effect) {
        return "effect clear @s minecraft:" + effect;
    }

    private static void effect(Feature feature, String effect, int amplifier) {
        TOGGLES.put(feature, new Toggle(List.of(give(effect, amplifier)), List.of(clear(effect))));
    }

    private static void attribute(Feature feature, String attribute, String on, String off) {
        TOGGLES.put(feature, new Toggle(List.of("attribute @s minecraft:" + attribute + " base set " + on),
                List.of("attribute @s minecraft:" + attribute + " base set " + off)));
    }

    /** On a server (not your own world) where you're OP. */
    public static boolean active(MinecraftClient client) {
        return client.player != null && !MineMenuClient.isSingleplayer(client) && client.player.hasPermissionLevel(2);
    }

    /** Your own world, or a server where you're OP: the pickers (give item, spawn mob, XP, time) work. */
    public static boolean screensAllowed(MinecraftClient client) {
        return MineMenuClient.isSingleplayer(client) || active(client);
    }

    /** True if this hack or action has a command version. */
    public static boolean supports(MenuEntry entry) {
        String id = entry.id();
        if (id.startsWith("feature:")) {
            try {
                return TOGGLES.containsKey(Feature.valueOf(id.substring(8)));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (id.startsWith("action:")) {
            try {
                return !actionCommands(MinecraftClient.getInstance(), ServerCheats.Action.valueOf(id.substring(7)), true).isEmpty();
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return SCREENS.contains(id);
    }

    public static boolean supports(Feature feature) {
        return TOGGLES.containsKey(feature);
    }

    /** Uses an entry with commands (call only when {@link #active} and {@link #supports}). */
    public static void run(MinecraftClient client, MenuEntry entry) {
        String id = entry.id();
        if (id.startsWith("feature:")) {
            toggle(client, Feature.valueOf(id.substring(8)));
        } else if (id.startsWith("action:")) {
            ServerCheats.Action action = ServerCheats.Action.valueOf(id.substring(7));
            send(client, actionCommands(client, action, false));
            if (action == ServerCheats.Action.SET_HOME) {
                setHome(client);
            }
        } else {
            entry.run().accept(client, null); // a picker screen: it sends the command itself
        }
    }

    public static void toggle(MinecraftClient client, Feature feature) {
        Toggle toggle = TOGGLES.get(feature);
        if (toggle == null) {
            return;
        }
        boolean on = CheatState.toggle(feature);
        send(client, on ? toggle.on() : toggle.off());
        client.player.sendMessage(Text.literal(feature.displayName() + ": ").append((on ? Text.translatable("minemenu.text.on")
                : Text.translatable("minemenu.text.off")).formatted(on ? Formatting.GREEN : Formatting.RED))
                .append(Text.translatable("minemenu.op.by_command").formatted(Formatting.GRAY)), true);
    }

    /** Leaving a server: the hacks you switched on there stay as they are on the server (they're its effects). */
    public static void send(MinecraftClient client, List<String> commands) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        for (String command : commands) {
            client.getNetworkHandler().sendChatCommand(command);
        }
    }

    // ------------------------------------------------------------------
    // Pickers
    // ------------------------------------------------------------------

    public static void giveItem(MinecraftClient client, Identifier item, int amount) {
        send(client, List.of("give @s " + item + " " + Math.max(1, amount)));
    }

    public static void spawnMob(MinecraftClient client, Identifier mob, int amount) {
        BlockPos at = target(client, 32);
        List<String> commands = new ArrayList<>();
        for (int i = 0; i < Math.max(1, Math.min(amount, 20)); i++) {
            commands.add("summon " + mob + " " + at.getX() + " " + (at.getY() + 1) + " " + at.getZ());
        }
        send(client, commands);
    }

    public static void giveXp(MinecraftClient client, int levels, boolean set) {
        send(client, List.of(set ? "xp set @s " + levels + " levels" : "xp add @s " + levels + " levels"));
    }

    public static void setTime(MinecraftClient client, int ticks) {
        send(client, List.of("time set " + ticks));
    }

    public static void setWeather(MinecraftClient client, String weather) {
        send(client, List.of("weather " + weather.toLowerCase(Locale.ROOT)));
    }

    /** Stopping time (speed 0) and normal days are possible by command; other day speeds aren't. */
    public static void setDaySpeed(MinecraftClient client, int speed) {
        send(client, List.of("gamerule doDaylightCycle " + (speed != 0)));
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** Commands for an action; empty = no command version. {@code probe}: only asking whether there is one. */
    private static List<String> actionCommands(MinecraftClient client, ServerCheats.Action action, boolean probe) {
        List<String> c = new ArrayList<>();
        switch (action) {
            case HEAL -> {
                c.add("effect give @s minecraft:instant_health 1 10 true");
                c.add("effect give @s minecraft:saturation 1 10 true");
            }
            case LAUNCH -> c.add("effect give @s minecraft:levitation 1 40 true");
            case DAY -> c.add("time set day");
            case NIGHT -> c.add("time set night");
            case CLEAR_WEATHER -> c.add("weather clear");
            case THUNDER -> c.add("weather thunder");
            case CLEAR_GROUND_ITEMS -> c.add("kill @e[type=minecraft:item,distance=..128]");
            case KILL_ALL -> c.add("kill @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,distance=..64]");
            case KILL_HOSTILE -> {
                for (String mob : HOSTILE) {
                    c.add("kill @e[type=minecraft:" + mob + ",distance=..64]");
                }
            }
            case NETHERITE_KIT -> {
                for (String item : new String[]{"netherite_sword", "netherite_pickaxe", "netherite_axe", "netherite_shovel", "netherite_helmet",
                        "netherite_chestplate", "netherite_leggings", "netherite_boots"}) {
                    c.add("give @s minecraft:" + item);
                }
                c.add("give @s minecraft:golden_apple 16");
            }
            case SET_HOME -> c.add("spawnpoint @s ~ ~ ~");
            case GO_HOME -> {
                Waypoints.Waypoint home = probe ? null : home();
                c.add(home == null ? "tp @s ~ ~ ~" : String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", home.x, home.y, home.z));
            }
            default -> {
                if (probe) {
                    return targetCommands(action, BlockPos.ORIGIN, null);
                }
                BlockPos at = target(client, 100);
                return targetCommands(action, at, client);
            }
        }
        return c;
    }

    /** Actions aimed at the block you look at. */
    private static List<String> targetCommands(ServerCheats.Action action, BlockPos at, MinecraftClient client) {
        List<String> c = new ArrayList<>();
        String xyz = at.getX() + " " + (at.getY() + 1) + " " + at.getZ();
        switch (action) {
            case TELEPORT -> c.add(String.format(Locale.ROOT, "tp @s %.1f %d %.1f", at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5));
            case SMITE -> c.add("summon minecraft:lightning_bolt " + xyz);
            case EXPLODE -> c.add("summon minecraft:tnt " + xyz + " {fuse:0}");
            case TNT_RAIN -> {
                for (int i = 0; i < 10; i++) {
                    c.add("summon minecraft:tnt " + (at.getX() + (i % 4) * 2 - 3) + " " + (at.getY() + 15) + " " + (at.getZ() + (i / 4) * 2 - 2) + " {fuse:60}");
                }
            }
            case ARROW_RAIN -> {
                for (int i = 0; i < 20; i++) {
                    c.add("summon minecraft:arrow " + (at.getX() + (i % 5) - 2) + " " + (at.getY() + 20) + " " + (at.getZ() + (i / 5) - 2));
                }
            }
            case MOB_RAIN -> {
                String[] mobs = {"zombie", "skeleton", "creeper", "spider", "pig", "cow", "sheep", "chicken"};
                for (int i = 0; i < mobs.length; i++) {
                    c.add("summon minecraft:" + mobs[i] + " " + (at.getX() + i - 4) + " " + (at.getY() + 15) + " " + at.getZ());
                }
            }
            case WOLF_ARMY -> {
                String owner = client == null ? "" : uuidArray(client.player.getUuid());
                for (int i = 0; i < 5; i++) {
                    c.add("summon minecraft:wolf ~ ~ ~ {Owner:" + owner + ",CollarColor:14b}");
                }
            }
            default -> {
            }
        }
        return c;
    }

    private static String uuidArray(java.util.UUID id) {
        long most = id.getMostSignificantBits(), least = id.getLeastSignificantBits();
        return "[I;" + (int) (most >> 32) + "," + (int) most + "," + (int) (least >> 32) + "," + (int) least + "]";
    }

    /** The block you're looking at (up to {@code reach} blocks), or the block under you. */
    private static BlockPos target(MinecraftClient client, double reach) {
        if (client.player == null) {
            return BlockPos.ORIGIN;
        }
        HitResult hit = client.player.raycast(reach, 0f, false);
        if (hit instanceof net.minecraft.util.hit.BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        return client.player.getBlockPos().down();
    }

    private static Waypoints.Waypoint home() {
        for (int i = 0; i < Waypoints.MAX; i++) {
            Waypoints.Waypoint w = Waypoints.get(i);
            if (w != null && "Home".equalsIgnoreCase(w.name)) {
                return w;
            }
        }
        return null;
    }

    private static void setHome(MinecraftClient client) {
        int slot = 0;
        for (int i = 0; i < Waypoints.MAX; i++) {
            Waypoints.Waypoint w = Waypoints.get(i);
            if (w != null && "Home".equalsIgnoreCase(w.name)) {
                slot = i;
                break;
            }
        }
        Waypoints.setHereClient(client, slot, "Home");
    }
}
