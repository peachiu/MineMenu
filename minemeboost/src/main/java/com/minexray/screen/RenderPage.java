package com.minexray.screen;

import com.minexray.BlockFilter;
import com.minexray.EspCategory;
import com.minexray.XrayConfig;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Render: how each mode looks and how far it reaches, in four groups (chosen with the buttons on top). */
final class RenderPage implements XrayPage {
    private enum Group { ESP, BLOCK_ESP, CLASSIC_CAVES, FLUIDS_ENTITIES }

    private static Group group = Group.ESP;
    private int x;
    private int y;
    private int column;
    private int index;
    private XrayScreen screen;

    @Override
    public void init(XrayScreen screen, int x, int top, int width, int height) {
        this.screen = screen;
        this.x = x;
        int groupWidth = (width - 12) / 4;
        for (Group g : Group.values()) {
            ButtonWidget b = ButtonWidget.builder(Text.translatable("minexray.render.group." + g.name().toLowerCase(java.util.Locale.ROOT)), btn -> {
                group = g;
                screen.rebuild();
            }).dimensions(x + g.ordinal() * (groupWidth + 4), top, groupWidth, 20).build();
            b.active = g != group;
            screen.add(b);
        }
        this.y = top + 26;
        this.column = Math.min(200, (width - 6) / 2);
        this.index = 0;
        XrayConfig c = XrayConfig.get();
        switch (group) {
            case ESP -> {
                screen.add(Widgets.cycle("minexray.render.style", names(XrayConfig.EspStyle.values()), () -> c.style, v -> c.style = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.slider("minexray.render.ore_radius", 8, 128, 4, () -> c.oreRadius, v -> c.oreRadius = v, nx(), ny(), column, "minexray.unit.blocks", null));
                next();
                screen.add(Widgets.slider("minexray.render.block_limit", 200, 10000, 100, () -> c.blockLimit, v -> c.blockLimit = v, nx(), ny(), column, null, null));
                next();
                screen.add(Widgets.slider("minexray.render.line_width", 1, 6, 1, () -> Math.round(c.lineWidth), v -> c.lineWidth = v, nx(), ny(), column, null, null));
                next();
                screen.add(Widgets.slider("minexray.render.line_opacity", 10, 100, 5, () -> c.lineOpacity, v -> c.lineOpacity = v, nx(), ny(), column, "minexray.unit.percent", null));
                next();
                screen.add(Widgets.slider("minexray.render.fill_opacity", 0, 100, 5, () -> c.fillOpacity, v -> c.fillOpacity = v, nx(), ny(), column, "minexray.unit.percent", null));
                next();
                screen.add(Widgets.toggle("minexray.render.fade", () -> c.fade, v -> c.fade = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.toggle("minexray.render.labels", () -> c.labels, v -> c.labels = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.toggle("minexray.render.tracers", () -> c.tracers, v -> c.tracers = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.slider("minexray.render.tracer_count", 1, 50, 1, () -> c.tracerCount, v -> c.tracerCount = v, nx(), ny(), column, null, null));
                next();
            }
            case BLOCK_ESP -> {
                screen.add(Widgets.slider("minexray.render.block_radius", 8, 128, 4, () -> c.blockEspRadius, v -> c.blockEspRadius = v, nx(), ny(), column, "minexray.unit.blocks", null));
                next();
                next();
                for (EspCategory category : EspCategory.values()) {
                    if (category.oreLayer()) {
                        continue;
                    }
                    String key = category.name();
                    ButtonWidget toggle = ButtonWidget.builder(label(category, c.categoryOn(category)), b -> {
                        c.categories.put(key, !c.categoryOn(category));
                        XrayConfig.markDirty();
                        b.setMessage(label(category, c.categoryOn(category)));
                        BlockFilter.rebuild();
                    }).dimensions(nx(), ny(), column - 26, 20).build();
                    screen.add(toggle);
                    screen.add(Widgets.color(() -> c.categoryColor(category), v -> {
                        c.categoryColors.put(key, v);
                        XrayConfig.markDirty();
                    }, nx() + column - 22, ny(), 22, BlockFilter::rebuild));
                    next();
                }
            }
            case CLASSIC_CAVES -> {
                screen.add(Widgets.slider("minexray.render.classic_opacity", 0, 100, 5, () -> c.classicOpacity, v -> c.classicOpacity = v, nx(), ny(), column, "minexray.unit.percent", null));
                next();
                screen.add(Widgets.toggle("minexray.render.classic_fluids", () -> c.classicFluids, v -> c.classicFluids = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.slider("minexray.render.cave_radius", 8, 128, 4, () -> c.caveRadius, v -> c.caveRadius = v, nx(), ny(), column, "minexray.unit.blocks", null));
                next();
                screen.add(Widgets.slider("minexray.render.cave_max_y", -64, 320, 8, () -> c.caveMaxY, v -> c.caveMaxY = v, nx(), ny(), column, null, null));
                next();
                screen.add(Widgets.cycle("minexray.render.cave_style", new String[]{"DOTS", "MESH"}, () -> c.caveStyle, v -> c.caveStyle = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.toggle("minexray.render.cave_large", () -> c.caveLargeOnly, v -> c.caveLargeOnly = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.slider("minexray.render.cave_volume", 50, 5000, 50, () -> c.caveMinVolume, v -> c.caveMinVolume = v, nx(), ny(), column, "minexray.unit.blocks", null));
                next();
            }
            case FLUIDS_ENTITIES -> {
                screen.add(Widgets.slider("minexray.render.fluid_radius", 8, 128, 4, () -> c.fluidRadius, v -> c.fluidRadius = v, nx(), ny(), column, "minexray.unit.blocks", null));
                next();
                screen.add(Widgets.toggle("minexray.render.lava_sound", () -> c.lavaWarningSound, v -> c.lavaWarningSound = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.toggle("minexray.render.fluid_lava", () -> c.fluidLava, v -> c.fluidLava = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.toggle("minexray.render.fluid_water", () -> c.fluidWater, v -> c.fluidWater = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.slider("minexray.render.entity_radius", 8, 128, 4, () -> c.entityRadius, v -> c.entityRadius = v, nx(), ny(), column, "minexray.unit.blocks", null));
                next();
                screen.add(Widgets.cycle("minexray.render.entity_style", new String[]{"OUTLINE", "BOX"}, () -> c.entityStyle, v -> c.entityStyle = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.toggle("minexray.render.entity_hostile", () -> c.entityHostile, v -> c.entityHostile = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.toggle("minexray.render.entity_passive", () -> c.entityPassive, v -> c.entityPassive = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.toggle("minexray.render.entity_players", () -> c.entityPlayers, v -> c.entityPlayers = v, nx(), ny(), column, null));
                next();
                screen.add(Widgets.toggle("minexray.render.entity_items", () -> c.entityItems, v -> c.entityItems = v, nx(), ny(), column, null));
                next();
                if (index % 2 == 1) {
                    next();
                }
                // Filter by mob type: ids separated by commas ("zombie, creeper"); empty = every type above.
                net.minecraft.client.gui.widget.TextFieldWidget only = new net.minecraft.client.gui.widget.TextFieldWidget(screen.font(), nx(), ny() + 1,
                        column * 2 + 6, 18, Text.translatable("minexray.render.entity_only"));
                only.setMaxLength(512);
                only.setPlaceholder(Text.translatable("minexray.render.entity_only").formatted(net.minecraft.util.Formatting.DARK_GRAY));
                only.setText(String.join(", ", c.entityOnly.stream().map(id -> id.startsWith("minecraft:") ? id.substring(10) : id).toList()));
                only.setChangedListener(text -> {
                    c.entityOnly.clear();
                    for (String part : text.split(",")) {
                        String id = part.trim().toLowerCase(java.util.Locale.ROOT);
                        if (!id.isEmpty()) {
                            c.entityOnly.add(id.contains(":") ? id : "minecraft:" + id);
                        }
                    }
                    XrayConfig.markDirty();
                });
                screen.add(only);
                next();
            }
        }
    }

    private static Text label(EspCategory category, boolean on) {
        return Text.literal(category.displayName() + ": ").append(Widgets.onOff(on));
    }

    private static String[] names(Enum<?>[] values) {
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    private int nx() {
        return x + (index % 2) * (column + 6);
    }

    private int ny() {
        return y + (index / 2) * 22;
    }

    private void next() {
        index++;
    }
}
