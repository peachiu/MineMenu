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
        screen[0] = new FormScreen(parent, "minemenu.form.item_creator", List.of(
                new FormScreen.Field("item", "minemenu.form.item", "diamond_sword"),
                new FormScreen.Field("count", "minemenu.form.count", "1"),
                new FormScreen.Field("name", "minemenu.form.name_c_red_l_bold", "&6Excalibur"),
                new FormScreen.Field("lore", "minemenu.form.lore_lines_split_by", "Forged in lava | Very sharp"),
                new FormScreen.Field("enchantments", "minemenu.form.enchantments_name_level", "sharpness 255, looting 10"),
                new FormScreen.Field("damage", "minemenu.form.attack_damage", "0"),
                new FormScreen.Field("attack speed", "minemenu.form.attack_speed", "0"),
                new FormScreen.Field("health", "minemenu.form.max_health", "0"),
                new FormScreen.Field("armor", "minemenu.form.armor", "0")),
                List.of("Unbreakable", "Hide tooltips"),
                List.of(new FormScreen.Action("minemenu.form.give", values -> {
                            Map<String, String> copy = new LinkedHashMap<>(values);
                            server(p -> Creators.createItem(p, copy));
                            screen[0].message("minemenu.form.given");
                        }),
                        new FormScreen.Action("minemenu.form.save_favorite", values -> {
                            CheatConfig.get().savedItems.add(GSON.toJson(values));
                            CheatConfig.markDirty();
                            screen[0].message("minemenu.form.saved_to_favorites");
                        }),
                        new FormScreen.Action("minemenu.form.favorites", values -> MinecraftClient.getInstance().setScreen(favorites(screen[0])))),
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
        return new PickerScreen(form, "minemenu.picker.saved_items_click_to_load", choices, choice -> {
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
        screen[0] = new FormScreen(parent, "minemenu.form.potion_creator", List.of(
                new FormScreen.Field("type", "minemenu.form.type_potion_splash_lingering_or_arrow", "potion"),
                new FormScreen.Field("name", "minemenu.form.name_optional", "&dSuper Potion"),
                new FormScreen.Field("effects", "minemenu.form.effects_name_level_seconds", "speed 5 60, strength 10 120"),
                new FormScreen.Field("color", "minemenu.form.colour_hex", "FF55FF")),
                List.of(),
                List.of(new FormScreen.Action("minemenu.form.give", values -> {
                    Map<String, String> copy = new LinkedHashMap<>(values);
                    server(p -> Creators.createPotion(p, copy));
                    screen[0].message("minemenu.form.given");
                })), potionValues);
        return screen[0];
    }

    static Screen mobCreator(Screen parent) {
        FormScreen[] screen = new FormScreen[1];
        screen[0] = new FormScreen(parent, "minemenu.form.mob_creator", List.of(
                new FormScreen.Field("mob", "minemenu.form.mob", "zombie"),
                new FormScreen.Field("name", "minemenu.form.name_optional", "&cBoss Bob"),
                new FormScreen.Field("health", "minemenu.form.health_0_normal", "100"),
                new FormScreen.Field("rider", "minemenu.form.rider_mob_optional", "skeleton"),
                new FormScreen.Field("equipment", "minemenu.form.head_chest_legs_feet_main_hand_off_hand", "diamond_helmet, , , , diamond_sword, shield"),
                new FormScreen.Field("effects", "minemenu.form.effects_name_level_seconds", "speed 2 999")),
                List.of("Baby", "No AI"),
                List.of(new FormScreen.Action("minemenu.form.spawn", values -> {
                            Map<String, String> copy = new LinkedHashMap<>(values);
                            server(p -> Creators.createMob(p, copy));
                            screen[0].message("minemenu.form.spawned_where_you_re_looking");
                        }),
                        new FormScreen.Action("minemenu.form.save_preset", values -> {
                            CheatConfig.get().mobPresets.add(GSON.toJson(values));
                            CheatConfig.markDirty();
                            screen[0].message("minemenu.form.preset_saved");
                        }),
                        new FormScreen.Action("minemenu.form.presets", values -> MinecraftClient.getInstance().setScreen(mobPresets(screen[0])))),
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
        return new PickerScreen(form, "minemenu.picker.mob_presets_click_to_load", choices, choice -> {
            Map<String, String> values = GSON.fromJson(saved.get(Integer.parseInt(choice.id())), new TypeToken<Map<String, String>>() { }.getType());
            if (values != null) {
                form.values().clear();
                form.values().putAll(values);
            }
            MinecraftClient.getInstance().setScreen(form);
        }, List.of());
    }
}
