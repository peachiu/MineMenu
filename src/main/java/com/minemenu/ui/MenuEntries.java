package com.minemenu.ui;

import com.minemenu.Category;
import com.minemenu.CheatState;
import com.minemenu.DeleteMode;
import com.minemenu.Feature;
import com.minemenu.ItemGiveScreen;
import com.minemenu.MineMenuClient;
import com.minemenu.MobSpawnScreen;
import com.minemenu.ServerCheats;
import com.minemenu.StructureScreen;
import com.minemenu.TimeWeatherScreen;
import com.minemenu.WaypointScreen;
import com.minemenu.Waypoints;
import com.minemenu.XpScreen;
import com.minemenu.core.CheatConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.integrated.IntegratedServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Everything the menu, the search box, the Command Palette, keys and macros can do, in one list. */
public final class MenuEntries {
    /** Screens added by other parts of the mod: id, category, name, description, how to open it. */
    private static final List<MenuEntry> screens = new ArrayList<>();
    /** Extra entries from MineMeBoost (FPS presets and tools), when MineBoost is in the same jar. */
    public static Supplier<List<MenuEntry>> extra = List::of;

    private MenuEntries() {
    }

    public static void screen(String id, Category category, String name, String description, Function<Screen, Screen> open) {
        screens.add(new MenuEntry("screen:" + id, name, description, category, MenuEntry.Kind.SCREEN, null,
                (client, parent) -> client.setScreen(open.apply(parent)), false));
    }

    static {
        screen("waypoints", Category.PLAYER, "Waypoints...", "Up to 10 named waypoints for this world: save, teleport, and see their direction and distance on screen.", WaypointScreen::new);
        screen("time_weather", Category.WORLD, "Time & Weather...", "Set the exact time, how fast days go by (or stop time), and sun, rain or thunder.", TimeWeatherScreen::new);
        screen("find_structure", Category.WORLD, "Find Structure...", "Find the nearest village, stronghold, ancient city, temple... Its direction and distance show on screen.", StructureScreen::new);
        screen("spawn_mob", Category.WORLD, "Spawn Mob...", "Pick any mob and spawn it where you're looking, or customize it first.", MobSpawnScreen::new);
        screen("give_item", Category.ITEMS, "Give Item...", "Pick any item and get it in your inventory, or dropped on the ground where you're looking.", ItemGiveScreen::new);
        screens.add(new MenuEntry("screen:delete_items", "Delete Items...", "Opens your inventory in delete mode: right-click any item to delete it.",
                Category.ITEMS, MenuEntry.Kind.SCREEN, null, (client, parent) -> DeleteMode.open(client), false));
        screen("give_xp", Category.ITEMS, "Give XP...", "Type a number of levels and get that much XP, e.g. 64.", XpScreen::new);
    }

    public static MenuEntry hack(Feature feature) {
        return new MenuEntry("feature:" + feature.name(), feature.displayName(), feature.description(), feature.category(),
                MenuEntry.Kind.HACK, () -> CheatState.isOn(feature),
                (client, parent) -> MineMenuClient.toggleWithMessage(client, feature), feature.allowedInMultiplayer());
    }

    public static MenuEntry action(ServerCheats.Action action) {
        return new MenuEntry("action:" + action.name(), action.displayName(), action.description(), action.category(),
                MenuEntry.Kind.ACTION, null, (client, parent) -> MineMenuClient.runAction(client, action), false);
    }

    /** Everything, in menu order: per page, hacks first, then screens, then actions; then waypoints and macros. */
    public static List<MenuEntry> all() {
        List<MenuEntry> list = new ArrayList<>();
        for (Category category : Category.values()) {
            list.addAll(page(category));
        }
        for (int i = 0; i < Waypoints.MAX; i++) {
            Waypoints.Waypoint w = Waypoints.get(i);
            if (w != null) {
                int slot = i;
                list.add(new MenuEntry("waypoint:" + slot, com.minemenu.core.Tr.format("minemenu.entry.go_to", "Go to %s", w.name), com.minemenu.core.Tr.format("minemenu.entry.go_to.desc", "Teleport to the waypoint %s.", w.name), null,
                        MenuEntry.Kind.WAYPOINT, null, onServer(player -> Waypoints.go(player, slot)), false));
            }
        }
        for (CheatConfig.Macro macro : CheatConfig.get().macros) {
            list.add(new MenuEntry("macro:" + macro.name, com.minemenu.core.Tr.format("minemenu.entry.macro", "Macro: %s", macro.name), String.join(", ", macro.steps), null,
                    MenuEntry.Kind.MACRO, null, (client, parent) -> com.minemenu.hud.Macros.run(macro), false));
        }
        list.addAll(extra.get());
        return list;
    }

    public static List<MenuEntry> page(Category category) {
        List<MenuEntry> list = new ArrayList<>();
        for (Feature feature : Feature.values()) {
            if (feature.category() == category) {
                list.add(hack(feature));
            }
        }
        for (MenuEntry screen : screens) {
            if (screen.category() == category) {
                list.add(screen);
            }
        }
        for (ServerCheats.Action action : ServerCheats.Action.values()) {
            if (action.category() == category) {
                list.add(action(action));
            }
        }
        return list;
    }

    public static MenuEntry byId(String id) {
        for (MenuEntry entry : all()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /** Runs something for the host on the integrated server thread. */
    public static BiConsumer<MinecraftClient, Screen> onServer(java.util.function.Consumer<net.minecraft.server.network.ServerPlayerEntity> action) {
        return (client, parent) -> {
            IntegratedServer server = client.getServer();
            if (server != null) {
                server.execute(() -> {
                    var player = ServerCheats.host(server);
                    if (player != null) {
                        com.minemenu.core.Guard.run("menu action", () -> action.accept(player));
                    }
                });
            }
        };
    }

    /** Runs an entry, refusing cheats on multiplayer servers with the usual message. */
    public static void run(MinecraftClient client, Screen parent, MenuEntry entry) {
        if (!entry.multiplayer() && !MineMenuClient.requireSingleplayer(client)) {
            return;
        }
        com.minemenu.hud.Macros.record(entry.id());
        com.minemenu.core.Guard.run(entry.name(), () -> entry.run().accept(client, parent));
    }
}
