package com.minemenu.items;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.minemenu.Category;
import com.minemenu.core.CheatConfig;
import com.minemenu.ui.FormScreen;
import com.minemenu.ui.MenuEntries;
import com.minemenu.ui.PickerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Items page's 3.4 screens: Item / Potion / Mob Creator, Inventory Snapshots, Armor Loadouts. */
public final class ItemScreens {
    private static final Gson GSON = new Gson();
    /** What you typed last time, so reopening a creator keeps it. */
    private static final Map<String, String> itemValues = new LinkedHashMap<>(Map.of("item", "diamond_sword", "name", "&6Excalibur",
            "enchantments", "sharpness 10, looting 5"));
    private static final Map<String, String> potionValues = new LinkedHashMap<>(Map.of("type", "potion", "effects", "speed 3 120, jump_boost 2 120"));
    private static final Map<String, String> mobValues = new LinkedHashMap<>(Map.of("mob", "skeleton", "rider", "", "equipment",
            "iron_helmet, iron_chestplate, , , bow"));

    private ItemScreens() {
    }

    public static void register() {
        MenuEntries.screen("item_creator", Category.ITEMS, "Item Creator...",
                "Make any item: name with &colors, lore, any enchantment up to level 255, attributes, unbreakable, hidden tooltips.", ItemScreens::itemCreator);
        MenuEntries.screen("potion_creator", Category.ITEMS, "Potion Creator...",
                "Make potions, splash/lingering potions or tipped arrows with any effects, levels and durations.", ItemScreens::potionCreator);
        MenuEntries.screen("mob_creator", Category.WORLD, "Mob Creator...",
                "Spawn a mob with equipment, effects, health, a name, as a baby, without AI, or with a rider (skeleton on a spider...).", ItemScreens::mobCreator);
        MenuEntries.screen("snapshots", Category.ITEMS, "Inventory Snapshots...",
                "Save your whole inventory (up to 5 per world) and get it back later.", parent -> new SlotsScreen(parent, false));
        MenuEntries.screen("loadouts", Category.COMBAT, "Armor Loadouts...",
                "Save sets of armor and weapons (up to 5) and switch between them with one click.", parent -> new SlotsScreen(parent, true));
    }

    private static void server(java.util.function.Consumer<net.minecraft.server.network.ServerPlayerEntity> action) {
        MenuEntries.onServer(action).accept(MinecraftClient.getInstance(), null);
    }

    static Screen itemCreator(Screen parent) {
        FormScreen[] screen = new FormScreen[1];
        screen[0] = new FormScreen(parent, "Item Creator", List.of(
                new FormScreen.Field("item", "Item", "diamond_sword"),
                new FormScreen.Field("count", "Count", "1"),
                new FormScreen.Field("name", "Name (&c = red, &l = bold...)", "&6Excalibur"),
                new FormScreen.Field("lore", "Lore (lines split by |)", "Forged in lava | Very sharp"),
                new FormScreen.Field("enchantments", "Enchantments (name level, ...)", "sharpness 255, looting 10"),
                new FormScreen.Field("damage", "Attack damage (+)", "0"),
                new FormScreen.Field("attack speed", "Attack speed (+)", "0"),
                new FormScreen.Field("health", "Max health (+)", "0"),
                new FormScreen.Field("armor", "Armor (+)", "0")),
                List.of("Unbreakable", "Hide tooltips"),
                List.of(new FormScreen.Action("Give", values -> {
                            Map<String, String> copy = new LinkedHashMap<>(values);
                            server(p -> Creators.createItem(p, copy));
                            screen[0].message("Given");
                        }),
                        new FormScreen.Action("Save favorite", values -> {
                            CheatConfig.get().savedItems.add(GSON.toJson(values));
                            CheatConfig.markDirty();
                            screen[0].message("Saved to favorites");
                        }),
                        new FormScreen.Action("Favorites...", values -> MinecraftClient.getInstance().setScreen(favorites(screen[0])))),
                itemValues);
        return screen[0];
    }

    private static Screen favorites(FormScreen form) {
        List<PickerScreen.Choice> choices = new ArrayList<>();
        List<String> saved = CheatConfig.get().savedItems;
        for (int i = 0; i < saved.size(); i++) {
            Map<String, String> values = GSON.fromJson(saved.get(i), new TypeToken<Map<String, String>>() { }.getType());
            String label = values == null ? "?" : values.getOrDefault("name", "").isBlank() ? values.getOrDefault("item", "?") : values.get("name").replaceAll("&.", "");
            choices.add(new PickerScreen.Choice(label, String.valueOf(i)));
        }
        return new PickerScreen(form, "Saved items (click to load)", choices, choice -> {
            Map<String, String> values = GSON.fromJson(saved.get(Integer.parseInt(choice.id())), new TypeToken<Map<String, String>>() { }.getType());
            if (values != null) {
                form.values().clear();
                form.values().putAll(values);
            }
            MinecraftClient.getInstance().setScreen(form);
        }, List.of());
    }

    static Screen potionCreator(Screen parent) {
        FormScreen[] screen = new FormScreen[1];
        screen[0] = new FormScreen(parent, "Potion Creator", List.of(
                new FormScreen.Field("type", "Type: potion, splash, lingering or arrow", "potion"),
                new FormScreen.Field("name", "Name (optional)", "&dSuper Potion"),
                new FormScreen.Field("effects", "Effects (name level seconds, ...)", "speed 5 60, strength 10 120"),
                new FormScreen.Field("color", "Colour (hex)", "FF55FF")),
                List.of(),
                List.of(new FormScreen.Action("Give", values -> {
                    Map<String, String> copy = new LinkedHashMap<>(values);
                    server(p -> Creators.createPotion(p, copy));
                    screen[0].message("Given");
                })), potionValues);
        return screen[0];
    }

    static Screen mobCreator(Screen parent) {
        FormScreen[] screen = new FormScreen[1];
        screen[0] = new FormScreen(parent, "Mob Creator", List.of(
                new FormScreen.Field("mob", "Mob", "zombie"),
                new FormScreen.Field("name", "Name (optional)", "&cBoss Bob"),
                new FormScreen.Field("health", "Health (0 = normal)", "100"),
                new FormScreen.Field("rider", "Rider mob (optional)", "skeleton"),
                new FormScreen.Field("equipment", "Head, chest, legs, feet, main hand, off hand", "diamond_helmet, , , , diamond_sword, shield"),
                new FormScreen.Field("effects", "Effects (name level seconds, ...)", "speed 2 999")),
                List.of("Baby", "No AI"),
                List.of(new FormScreen.Action("Spawn", values -> {
                            Map<String, String> copy = new LinkedHashMap<>(values);
                            server(p -> Creators.createMob(p, copy));
                            screen[0].message("Spawned where you're looking");
                        }),
                        new FormScreen.Action("Save preset", values -> {
                            CheatConfig.get().mobPresets.add(GSON.toJson(values));
                            CheatConfig.markDirty();
                            screen[0].message("Preset saved");
                        }),
                        new FormScreen.Action("Presets...", values -> MinecraftClient.getInstance().setScreen(mobPresets(screen[0])))),
                mobValues);
        return screen[0];
    }

    private static Screen mobPresets(FormScreen form) {
        List<PickerScreen.Choice> choices = new ArrayList<>();
        List<String> saved = CheatConfig.get().mobPresets;
        for (int i = 0; i < saved.size(); i++) {
            Map<String, String> values = GSON.fromJson(saved.get(i), new TypeToken<Map<String, String>>() { }.getType());
            String label = values == null ? "?" : values.getOrDefault("name", "").isBlank() ? values.getOrDefault("mob", "?") : values.get("name").replaceAll("&.", "");
            choices.add(new PickerScreen.Choice(label, String.valueOf(i)));
        }
        return new PickerScreen(form, "Mob presets (click to load)", choices, choice -> {
            Map<String, String> values = GSON.fromJson(saved.get(Integer.parseInt(choice.id())), new TypeToken<Map<String, String>>() { }.getType());
            if (values != null) {
                form.values().clear();
                form.values().putAll(values);
            }
            MinecraftClient.getInstance().setScreen(form);
        }, List.of());
    }
}
