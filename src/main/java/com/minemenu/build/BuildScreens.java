package com.minemenu.build;

import com.minemenu.Category;
import com.minemenu.ui.MenuEntries;
import com.minemenu.ui.PickerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** The Build page's screens: Schematics, Structures, Brush mode and Biome Painter (all built on PickerScreen). */
public final class BuildScreens {
    private BuildScreens() {
    }

    public static void register() {
        MenuEntries.screen("schematics", Category.BUILD, "Schematics...",
                "Save the copy as a .nbt file (config/minemeboost/schematics) or load one to paste, in any world.", BuildScreens::schematics);
        MenuEntries.screen("structures", Category.BUILD, "Structures...",
                "Place a ready-made build where you look: house, tower, castle, bridge, farm, Nether portal, or a real village, temple...", BuildScreens::structures);
        MenuEntries.screen("brush", Category.BUILD, "Brush...",
                "Pick what the Terrain Brush does: flatten, raise, lower, smooth, paint, replace or drain.", BuildScreens::brush);
        MenuEntries.screen("biome_painter", Category.BUILD, "Biome Painter...",
                "Change the biome of the selection (like /fillbiome): grass, leaves, water colour, weather and mob spawns follow.", BuildScreens::biomes);
    }

    private static void server(java.util.function.Consumer<net.minecraft.server.network.ServerPlayerEntity> action) {
        MenuEntries.onServer(action).accept(MinecraftClient.getInstance(), null);
    }

    static Screen schematics(Screen parent) {
        return new SchematicBrowserScreen(parent);
    }

    static Screen structures(Screen parent) {
        List<PickerScreen.Choice> choices = new ArrayList<>();
        for (Structures.Built built : Structures.Built.values()) {
            String name = built.name().charAt(0) + built.name().substring(1).toLowerCase().replace('_', ' ');
            choices.add(new PickerScreen.Choice(name, "built:" + built.name()));
        }
        for (String[] vanilla : Structures.VANILLA) {
            choices.add(new PickerScreen.Choice(vanilla[1] + " (vanilla)", "vanilla:" + vanilla[0]));
        }
        return new PickerScreen(parent, "Structures (placed where you look)", choices, choice -> {
            MinecraftClient.getInstance().setScreen(null);
            if (choice.id().startsWith("built:")) {
                Structures.Built built = Structures.Built.valueOf(choice.id().substring(6));
                server(p -> Structures.place(p, built));
            } else {
                String id = choice.id().substring(8);
                server(p -> Structures.placeVanilla(p, id));
            }
        }, List.of());
    }

    static Screen brush(Screen parent) {
        List<PickerScreen.Choice> choices = new ArrayList<>();
        for (TerrainBrush.Mode mode : TerrainBrush.Mode.values()) {
            String label = mode.name().charAt(0) + mode.name().substring(1).toLowerCase() + (mode == TerrainBrush.mode() ? " ✔" : "");
            choices.add(new PickerScreen.Choice(label, mode.name()));
        }
        return new PickerScreen(parent, "Terrain Brush: right-click with a golden shovel", choices, choice -> {
            TerrainBrush.setMode(TerrainBrush.Mode.valueOf(choice.id()));
            MinecraftClient.getInstance().setScreen(brush(parent));
        }, List.of());
    }

    static Screen biomes(Screen parent) {
        List<PickerScreen.Choice> choices = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            for (var id : client.world.getRegistryManager().get(RegistryKeys.BIOME).getIds()) {
                choices.add(new PickerScreen.Choice(id.getPath().replace('_', ' '), id.toString()));
            }
        }
        choices.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
        return new PickerScreen(parent, "Biome Painter (applied to the selection)", choices, choice -> {
            MinecraftClient.getInstance().setScreen(null);
            server(p -> BuildTools.paintBiome(p, choice.id()));
        }, List.of());
    }
}
