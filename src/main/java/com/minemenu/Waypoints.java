package com.minemenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Up to 10 named waypoints per world (5 before 3.4), plus "Last Death",, saved in the world folder (minemenu_waypoints.json), with a
 * direction-and-distance line in the HUD. Replaces the single Home of MineMenu 3.2: an old Home is
 * moved into the first slot, and Set Home / Go Home use the waypoint called "Home".
 *
 * <p>Changed on the server thread; the HUD and the Waypoints screen read a copy on the client.
 */
public final class Waypoints {
    public static final int MAX = 10;
    private static final Logger LOGGER = LoggerFactory.getLogger("MineMenu");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String HOME = "Home";

    /** One saved spot. Plain fields so Gson can read and write it. */
    public static final class Waypoint {
        public String name;
        public String dimension;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public boolean hud = true;

        Waypoint copy() {
            Waypoint w = new Waypoint();
            w.name = name;
            w.dimension = dimension;
            w.x = x;
            w.y = y;
            w.z = z;
            w.yaw = yaw;
            w.pitch = pitch;
            w.hud = hud;
            return w;
        }
    }

    /** Saved file layout since 3.4 (the 3.3 file was just the array of slots). */
    private static final class SaveFile {
        Waypoint[] slots;
        Waypoint lastDeath;
    }

    /** A Structure Finder result, shown in the HUD like a waypoint until you search again. */
    public record Found(String name, String dimension, double x, double z) {
    }

    private static volatile Waypoint[] slots = new Waypoint[MAX];
    /** Where you died last (set automatically, shown in the HUD until you get there or delete it). */
    private static volatile Waypoint lastDeath;
    private static volatile Found found;
    private static volatile int version;
    private static Path file;

    private Waypoints() {
    }

    // ------------------------------------------------------------------
    // Loading and saving (server thread)
    // ------------------------------------------------------------------

    public static void onServerStarted(MinecraftServer server) {
        found = null;
        Path root = server.getSavePath(WorldSavePath.ROOT);
        file = root.resolve("minemenu_waypoints.json");
        Waypoint[] loaded = new Waypoint[MAX];
        Waypoint death = null;
        try {
            if (Files.exists(file)) {
                String json = Files.readString(file).trim();
                Waypoint[] read;
                if (json.startsWith("[")) {
                    read = GSON.fromJson(json, Waypoint[].class); // 3.3 format
                } else {
                    SaveFile saved = GSON.fromJson(json, SaveFile.class);
                    read = saved != null ? saved.slots : null;
                    death = saved != null && valid(saved.lastDeath) ? saved.lastDeath : null;
                }
                for (int i = 0; read != null && i < Math.min(MAX, read.length); i++) {
                    loaded[i] = valid(read[i]) ? read[i] : null;
                }
            } else {
                // MineMenu 3.2 had one Home in minemenu_home.txt: "dimension x y z yaw pitch".
                Path home = root.resolve("minemenu_home.txt");
                if (Files.exists(home)) {
                    String[] parts = Files.readString(home).trim().split(" ");
                    Waypoint w = new Waypoint();
                    w.name = HOME;
                    w.dimension = parts[0];
                    w.x = Double.parseDouble(parts[1]);
                    w.y = Double.parseDouble(parts[2]);
                    w.z = Double.parseDouble(parts[3]);
                    w.yaw = Float.parseFloat(parts[4]);
                    w.pitch = Float.parseFloat(parts[5]);
                    loaded[0] = w;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Couldn't read the MineMenu waypoints of this world", e);
        }
        slots = loaded;
        lastDeath = death;
        version++;
        if (Files.exists(root.resolve("minemenu_home.txt")) && !Files.exists(file)) {
            save(); // finish the move to the new file
        }
    }

    private static void save() {
        if (file == null) {
            return;
        }
        try {
            SaveFile saved = new SaveFile();
            saved.slots = slots;
            saved.lastDeath = lastDeath;
            Files.writeString(file, GSON.toJson(saved));
            // Keep the 3.2 Home file up to date too, so an older MineMenu still finds your Home.
            int homeSlot = indexOf(HOME);
            Waypoint home = homeSlot >= 0 ? slots[homeSlot] : null;
            if (home != null) {
                Files.writeString(file.resolveSibling("minemenu_home.txt"), home.dimension + " " + home.x + " " + home.y
                        + " " + home.z + " " + home.yaw + " " + home.pitch);
            }
        } catch (Exception e) {
            LOGGER.warn("Couldn't save the MineMenu waypoints", e);
        }
    }

    private static int indexOf(String name) {
        Waypoint[] now = slots;
        for (int i = 0; i < MAX; i++) {
            if (now[i] != null && now[i].name != null && now[i].name.equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static void changed() {
        version++;
        save();
    }

    // ------------------------------------------------------------------
    // Changes (server thread)
    // ------------------------------------------------------------------

    /** Saves where you're standing in {@code slot}, keeping its name (or giving it one). */
    public static void setHere(ServerPlayerEntity player, int slot, String name) {
        Waypoint[] copy = slots.clone();
        Waypoint w = new Waypoint();
        Waypoint old = copy[slot];
        w.name = name != null && !name.isBlank() ? name.trim() : old != null ? old.name : com.minemenu.core.Tr.get("minemenu.waypoint.default_name", "Waypoint") + " " + (slot + 1);
        w.hud = old == null || old.hud;
        w.dimension = player.getServerWorld().getRegistryKey().getValue().toString();
        w.x = player.getX();
        w.y = player.getY();
        w.z = player.getZ();
        w.yaw = player.getYaw();
        w.pitch = player.getPitch();
        copy[slot] = w;
        slots = copy;
        changed();
        player.sendMessage(Text.translatable("minemenu.text.waypoint_s_set_here", w.name).formatted(Formatting.GREEN), true);
    }

    public static void go(ServerPlayerEntity player, int slot) {
        Waypoint w = slots[slot];
        if (w != null) {
            teleport(player, w);
        }
    }

    private static void teleport(ServerPlayerEntity player, Waypoint w) {
        Identifier id = Identifier.tryParse(w.dimension);
        MinecraftServer server = player.getServer();
        ServerWorld world = server == null || id == null ? null
                : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
        if (world == null) {
            player.sendMessage(Text.translatable("minemenu.text.that_dimension_doesn_t_exist_any").formatted(Formatting.RED), true);
            return;
        }
        player.teleport(world, w.x, w.y, w.z, w.yaw, w.pitch);
        player.fallDistance = 0;
        player.sendMessage((HOME.equalsIgnoreCase(w.name) ? Text.translatable("minemenu.text.welcome_home") : Text.translatable("minemenu.text.teleported_to_s", w.name))
                .formatted(Formatting.GREEN), true);
    }

    // Changes replace the waypoint with a changed copy: the HUD (client thread) may be reading the old one.

    public static void rename(int slot, String name) {
        Waypoint w = slots[slot];
        if (w != null && name != null && !name.isBlank() && !name.trim().equals(w.name)) {
            Waypoint changedCopy = w.copy();
            changedCopy.name = name.trim();
            replace(slot, changedCopy);
        }
    }

    public static void toggleHud(int slot) {
        Waypoint w = slots[slot];
        if (w != null) {
            Waypoint changedCopy = w.copy();
            changedCopy.hud = !w.hud;
            replace(slot, changedCopy);
        }
    }

    private static void replace(int slot, Waypoint w) {
        Waypoint[] copy = slots.clone();
        copy[slot] = w;
        slots = copy;
        changed();
    }

    /** A waypoint with a dimension id Minecraft accepts (hand-edited files can have anything). */
    private static boolean valid(Waypoint w) {
        return w != null && w.dimension != null && Identifier.tryParse(w.dimension) != null
                && Double.isFinite(w.x) && Double.isFinite(w.y) && Double.isFinite(w.z);
    }

    /** The host died: remember where (for Last Death, and Death Chest's waypoint). */
    public static void setLastDeath(ServerPlayerEntity player, String name) {
        Waypoint w = new Waypoint();
        w.name = name;
        w.dimension = player.getServerWorld().getRegistryKey().getValue().toString();
        w.x = player.getX();
        w.y = player.getY();
        w.z = player.getZ();
        w.yaw = player.getYaw();
        w.pitch = player.getPitch();
        lastDeath = w;
        changed();
    }

    public static Waypoint lastDeath() {
        return lastDeath;
    }

    public static void goLastDeath(ServerPlayerEntity player) {
        Waypoint w = lastDeath;
        if (w == null) {
            player.sendMessage(Text.translatable("minemenu.text.you_haven_t_died_in_this").formatted(Formatting.YELLOW), true);
            return;
        }
        teleport(player, w);
    }

    public static void deleteLastDeath() {
        lastDeath = null;
        changed();
    }

    public static void delete(int slot) {
        Waypoint[] copy = slots.clone();
        copy[slot] = null;
        slots = copy;
        changed();
    }

    /** Set Home: the waypoint called Home, or the first free slot. */
    public static void setHome(ServerPlayerEntity player) {
        int slot = indexOf(HOME);
        if (slot < 0) {
            slot = firstFree();
        }
        if (slot < 0) {
            player.sendMessage(Text.translatable("minemenu.text.all_10_waypoints_are_used_delete").formatted(Formatting.YELLOW), true);
            return;
        }
        setHere(player, slot, HOME);
    }

    public static void goHome(ServerPlayerEntity player) {
        int slot = indexOf(HOME);
        if (slot < 0) {
            player.sendMessage(Text.translatable("minemenu.text.no_home_yet_use_set_home").formatted(Formatting.YELLOW), true);
            return;
        }
        go(player, slot);
    }

    private static int firstFree() {
        Waypoint[] now = slots;
        for (int i = 0; i < MAX; i++) {
            if (now[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public static void setFound(Found result) {
        found = result;
        version++;
    }

    // ------------------------------------------------------------------
    // Reading (any thread)
    // ------------------------------------------------------------------

    public static Waypoint get(int slot) {
        return slots[slot];
    }

    /** Goes up whenever something changes, so screens know to refresh. */
    public static int version() {
        return version;
    }

    /** The HUD lines: "Home 120m ↗" for waypoints (and a found structure) in your dimension. */
    public static List<Text> hudLines(MinecraftClient client) {
        List<Text> lines = new ArrayList<>();
        if (client.player == null || client.world == null) {
            return lines;
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        for (Waypoint w : slots) {
            if (w != null && w.hud && dimension.equals(w.dimension)) {
                lines.add(line(client, w.name, w.x, w.y, w.z, Formatting.AQUA));
            }
        }
        Waypoint death = lastDeath;
        if (death != null && dimension.equals(death.dimension)) {
            lines.add(line(client, death.name, death.x, death.y, death.z, Formatting.RED));
        }
        Found f = found;
        if (f != null && dimension.equals(f.dimension())) {
            lines.add(line(client, f.name(), f.x(), Double.NaN, f.z(), Formatting.GOLD));
        }
        return lines;
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static Text line(MinecraftClient client, String name, double x, double y, double z, Formatting color) {
        var player = client.player;
        double dx = x - player.getX();
        double dz = z - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        // Minecraft yaw: 0 = south (+Z), 90 = west (-X), turning right makes it bigger.
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        String arrow = distance < 2 ? "•" : ARROWS[Math.floorMod((int) Math.round(relative / 45.0), 8)];
        String height = "";
        if (!Double.isNaN(y)) {
            int dy = (int) Math.round(y - player.getY());
            if (Math.abs(dy) >= 4) {
                height = dy > 0 ? " ▲" + dy : " ▼" + (-dy);
            }
        }
        return Text.literal(arrow + " ").formatted(Formatting.WHITE, Formatting.BOLD)
                .append(Text.literal(name).formatted(color))
                .append(Text.literal(" " + distance + "m" + height).formatted(Formatting.GRAY));
    }

    /** For the Waypoints screen: "x, y, z (dimension)". */
    public static String describe(Waypoint w) {
        String dim = w.dimension.equals(World.OVERWORLD.getValue().toString()) ? com.minemenu.core.Tr.get("minemenu.dimension.overworld", "Overworld")
                : w.dimension.equals(World.NETHER.getValue().toString()) ? com.minemenu.core.Tr.get("minemenu.dimension.nether", "Nether")
                : w.dimension.equals(World.END.getValue().toString()) ? com.minemenu.core.Tr.get("minemenu.dimension.end", "The End") : w.dimension;
        return (int) Math.floor(w.x) + ", " + (int) Math.floor(w.y) + ", " + (int) Math.floor(w.z) + " (" + dim + ")";
    }
}
