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
 * Up to 5 named waypoints per world, saved in the world folder (minemenu_waypoints.json), with a
 * direction-and-distance line in the HUD. Replaces the single Home of MineMenu 3.2: an old Home is
 * moved into the first slot, and Set Home / Go Home use the waypoint called "Home".
 *
 * <p>Changed on the server thread; the HUD and the Waypoints screen read a copy on the client.
 */
public final class Waypoints {
    public static final int MAX = 5;
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
    }

    /** A Structure Finder result, shown in the HUD like a waypoint until you search again. */
    public record Found(String name, String dimension, double x, double z) {
    }

    private static volatile Waypoint[] slots = new Waypoint[MAX];
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
        try {
            if (Files.exists(file)) {
                Waypoint[] read = GSON.fromJson(Files.readString(file), Waypoint[].class);
                for (int i = 0; read != null && i < Math.min(MAX, read.length); i++) {
                    loaded[i] = read[i] != null && read[i].dimension != null ? read[i] : null;
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
            Files.writeString(file, GSON.toJson(slots));
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
        w.name = name != null && !name.isBlank() ? name.trim() : old != null ? old.name : "Waypoint " + (slot + 1);
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
        player.sendMessage(Text.literal("Waypoint \"" + w.name + "\" set here").formatted(Formatting.GREEN), true);
    }

    public static void go(ServerPlayerEntity player, int slot) {
        Waypoint w = slots[slot];
        if (w == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        ServerWorld world = server == null ? null
                : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(w.dimension)));
        if (world == null) {
            player.sendMessage(Text.literal("That dimension doesn't exist any more").formatted(Formatting.RED), true);
            return;
        }
        player.teleport(world, w.x, w.y, w.z, w.yaw, w.pitch);
        player.fallDistance = 0;
        player.sendMessage(Text.literal(HOME.equalsIgnoreCase(w.name) ? "Welcome home!" : "Teleported to " + w.name)
                .formatted(Formatting.GREEN), true);
    }

    public static void rename(int slot, String name) {
        Waypoint w = slots[slot];
        if (w != null && name != null && !name.isBlank() && !name.trim().equals(w.name)) {
            w.name = name.trim();
            changed();
        }
    }

    public static void toggleHud(int slot) {
        Waypoint w = slots[slot];
        if (w != null) {
            w.hud = !w.hud;
            changed();
        }
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
            player.sendMessage(Text.literal("All 5 waypoints are used: delete one in Waypoints... first").formatted(Formatting.YELLOW), true);
            return;
        }
        setHere(player, slot, HOME);
    }

    public static void goHome(ServerPlayerEntity player) {
        int slot = indexOf(HOME);
        if (slot < 0) {
            player.sendMessage(Text.literal("No home yet: use Set Home first").formatted(Formatting.YELLOW), true);
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
        String dim = w.dimension.equals(World.OVERWORLD.getValue().toString()) ? "Overworld"
                : w.dimension.equals(World.NETHER.getValue().toString()) ? "Nether"
                : w.dimension.equals(World.END.getValue().toString()) ? "The End" : w.dimension;
        return (int) Math.floor(w.x) + ", " + (int) Math.floor(w.y) + ", " + (int) Math.floor(w.z) + " (" + dim + ")";
    }
}
