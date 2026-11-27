package com.minexray.screen;

import com.minexray.PresetManager;
import com.minexray.XrayConfig;
import com.minexray.XrayPreset;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Presets: pick one, save a copy, rename, delete, share as a code (copied to / pasted from the clipboard), put
 * the built-in ones back, and choose which preset each dimension switches to by itself.
 */
final class PresetsPage implements XrayPage {
    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
    private static String selected;
    private static boolean confirmDelete;
    private static String message = "";
    private static int scroll;
    private int x;
    private int bottomY;
    private int rowsShown;
    private int listSize;

    @Override
    public void init(XrayScreen screen, int x, int y, int width, int height) {
        this.x = x;
        List<XrayPreset> presets = PresetManager.all();
        listSize = presets.size();
        if (selected == null || PresetManager.get(selected) == null) {
            selected = PresetManager.active().name;
        }
        int listWidth = Math.min(170, width / 2 - 4);
        rowsShown = Math.max(3, (height - 80) / 22);
        scroll = Math.max(0, Math.min(scroll, presets.size() - rowsShown));
        for (int i = scroll; i < Math.min(presets.size(), scroll + rowsShown); i++) {
            XrayPreset preset = presets.get(i);
            boolean active = preset.name.equals(XrayConfig.get().activePreset);
            Text label = Text.literal((active ? "● " : "") + preset.name).formatted(preset.name.equals(selected) ? Formatting.YELLOW
                    : active ? Formatting.GREEN : Formatting.WHITE);
            screen.add(ButtonWidget.builder(label, b -> {
                selected = preset.name;
                confirmDelete = false;
                screen.rebuild();
            }).dimensions(x, y + (i - scroll) * 22, listWidth, 20).build());
        }
        int bx = x + listWidth + 8;
        int bw = Math.min(150, width - listWidth - 8);
        int by = y;
        screen.add(ButtonWidget.builder(Text.translatable("minexray.presets.use").formatted(Formatting.GREEN), b -> {
            PresetManager.apply(selected, true);
            message = Text.translatable("minexray.preset.now", selected).getString();
            screen.rebuild();
        }).dimensions(bx, by, bw, 20).tooltip(Tooltip.of(Text.translatable("minexray.presets.use.tooltip"))).build());
        by += 22;
        TextFieldWidget nameField = new TextFieldWidget(screen.font(), bx, by + 1, bw, 18, Text.translatable("minexray.presets.name"));
        nameField.setText(selected);
        nameField.setMaxLength(32);
        screen.add(nameField);
        by += 22;
        screen.add(ButtonWidget.builder(Text.translatable("minexray.presets.rename"), b -> {
            String name = nameField.getText();
            if (PresetManager.rename(selected, name)) {
                selected = name.trim();
            }
            screen.rebuild();
        }).dimensions(bx, by, bw / 2 - 2, 20).build());
        screen.add(ButtonWidget.builder(Text.translatable("minexray.presets.duplicate"), b -> {
            String copy = PresetManager.duplicate(selected);
            if (copy != null) {
                selected = copy;
            }
            screen.rebuild();
        }).dimensions(bx + bw / 2 + 2, by, bw / 2 - 2, 20).build());
        by += 22;
        ButtonWidget delete = screen.add(ButtonWidget.builder(Text.translatable(confirmDelete ? "minexray.presets.delete_confirm" : "minexray.presets.delete")
                .formatted(Formatting.RED), b -> {
            if (!confirmDelete) {
                confirmDelete = true;
            } else {
                PresetManager.delete(selected);
                selected = null;
                confirmDelete = false;
            }
            screen.rebuild();
        }).dimensions(bx, by, bw, 20).build());
        delete.active = listSize > 1;
        by += 22;
        screen.add(ButtonWidget.builder(Text.translatable("minexray.presets.export"), b -> {
            screen.mc().keyboard.setClipboard(PresetManager.export(selected));
            message = Text.translatable("minexray.presets.exported").getString();
        }).dimensions(bx, by, bw / 2 - 2, 20).tooltip(Tooltip.of(Text.translatable("minexray.presets.export.tooltip"))).build());
        screen.add(ButtonWidget.builder(Text.translatable("minexray.presets.import"), b -> {
            String name = PresetManager.importCode(screen.mc().keyboard.getClipboard());
            message = name == null ? Text.translatable("minexray.presets.import_failed").getString() : Text.translatable("minexray.presets.imported", name).getString();
            if (name != null) {
                selected = name;
            }
            screen.rebuild();
        }).dimensions(bx + bw / 2 + 2, by, bw / 2 - 2, 20).tooltip(Tooltip.of(Text.translatable("minexray.presets.import.tooltip"))).build());
        by += 22;
        screen.add(ButtonWidget.builder(Text.translatable("minexray.presets.restore"), b -> {
            PresetManager.restoreBuiltIns();
            message = Text.translatable("minexray.presets.restored").getString();
            screen.rebuild();
        }).dimensions(bx, by, bw, 20).build());
        // Per dimension.
        int dy = y + rowsShown * 22 + 6;
        XrayConfig config = XrayConfig.get();
        screen.add(Widgets.toggle("minexray.presets.auto_dimension", () -> config.autoDimension, v -> config.autoDimension = v, x, dy, listWidth, null));
        String[] names = presets.stream().map(p -> p.name).toArray(String[]::new);
        String[] withNone = new String[names.length + 1];
        withNone[0] = "-";
        System.arraycopy(names, 0, withNone, 1, names.length);
        int dx = x;
        dy += 22;
        int dw = Math.min(150, (width - 8) / 3);
        for (String dimension : DIMENSIONS) {
            String short_ = dimension.substring(dimension.indexOf(':') + 1);
            screen.add(ButtonWidget.builder(dimensionLabel(short_, config.dimensionPresets.getOrDefault(dimension, "-")), b -> {
                String current = config.dimensionPresets.getOrDefault(dimension, "-");
                int i = java.util.Arrays.asList(withNone).indexOf(current);
                String next = withNone[(i + 1) % withNone.length];
                if (next.equals("-")) {
                    config.dimensionPresets.remove(dimension);
                } else {
                    config.dimensionPresets.put(dimension, next);
                }
                XrayConfig.markDirty();
                b.setMessage(dimensionLabel(short_, next));
            }).dimensions(dx, dy, dw, 20).build());
            dx += dw + 4;
        }
        bottomY = dy + 26;
    }

    private static Text dimensionLabel(String dimension, String preset) {
        return Text.translatable("minexray.presets.dimension." + dimension).append(": ").append(Text.literal(preset).formatted(Formatting.YELLOW));
    }

    @Override
    public void render(XrayScreen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        XrayPreset preset = PresetManager.get(selected);
        if (preset != null) {
            context.drawTextWithShadow(screen.font(), Text.translatable("minexray.presets.info", preset.blocks.size(),
                    Text.translatable(preset.builtIn ? "minexray.presets.built_in" : "minexray.presets.yours")).formatted(Formatting.GRAY), x, bottomY, 0xFFFFFF);
        }
        if (!message.isEmpty()) {
            context.drawTextWithShadow(screen.font(), Text.literal(message).formatted(Formatting.AQUA), x, bottomY + 12, 0xFFFFFF);
        }
    }

    @Override
    public boolean scrolled(XrayScreen screen, double amount) {
        int before = scroll;
        scroll = Math.max(0, Math.min(Math.max(0, listSize - rowsShown), scroll - (int) Math.signum(amount)));
        if (before != scroll) {
            screen.rebuild();
        }
        return true;
    }
}
