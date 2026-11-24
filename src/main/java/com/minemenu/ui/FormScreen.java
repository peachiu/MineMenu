package com.minemenu.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A simple form in two columns: text fields (with a grey label above each), ON/OFF toggles and buttons.
 * Values are kept in a map so they survive window resizes and can be saved as presets.
 */
public class FormScreen extends Screen {
    public record Field(String key, String label, String hint) {
    }

    public record Action(String label, Consumer<Map<String, String>> run) {
    }

    private final Screen parent;
    private final String heading;
    private final List<Field> fields;
    private final List<String> toggles;
    private final List<Action> actions;
    private final Map<String, String> values;
    private String message = "";

    public FormScreen(Screen parent, String heading, List<Field> fields, List<String> toggles, List<Action> actions, Map<String, String> values) {
        super(Text.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.fields = fields;
        this.toggles = toggles;
        this.actions = actions;
        this.values = values == null ? new LinkedHashMap<>() : values;
    }

    public Map<String, String> values() {
        return values;
    }

    public void message(String text) {
        this.message = text;
    }

    @Override
    protected void init() {
        int columnWidth = Math.min(200, (this.width - 24) / 2);
        int left = this.width / 2 - columnWidth - 4;
        int rows = (fields.size() + 1) / 2;
        int rowHeight = this.height < 300 ? 30 : 34;
        int top = 24;
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            int x = left + (i % 2) * (columnWidth + 8);
            int y = top + (i / 2) * rowHeight + 10;
            TextFieldWidget widget = new TextFieldWidget(this.textRenderer, x, y, columnWidth, 18, Text.literal(field.label()));
            widget.setMaxLength(512);
            widget.setText(values.getOrDefault(field.key(), ""));
            widget.setPlaceholder(Text.literal(field.hint()).formatted(Formatting.DARK_GRAY));
            widget.setChangedListener(text -> values.put(field.key(), text));
            addDrawableChild(widget);
        }
        int y = top + rows * rowHeight + 4;
        int toggleWidth = Math.max(80, (columnWidth * 2 + 8 - (toggles.size() - 1) * 4) / Math.max(1, toggles.size()));
        for (int i = 0; i < toggles.size(); i++) {
            String key = toggles.get(i);
            boolean on = "true".equals(values.get(key));
            addDrawableChild(ButtonWidget.builder(Text.literal(key + ": " + (on ? "ON" : "OFF")), b -> {
                values.put(key, on ? "false" : "true");
                clearAndInit();
            }).dimensions(left + i * (toggleWidth + 4), y, toggleWidth, 20).build());
        }
        int bottom = this.height - 24;
        int buttonWidth = Math.min(100, (columnWidth * 2 + 8 - actions.size() * 4) / (actions.size() + 1));
        int x = this.width / 2 - ((actions.size() + 1) * (buttonWidth + 4) - 4) / 2;
        for (Action action : actions) {
            addDrawableChild(ButtonWidget.builder(Text.literal(action.label()), b -> action.run().accept(values))
                    .dimensions(x, bottom, buttonWidth, 20).tooltip(Tooltip.of(Text.literal(action.label()))).build());
            x += buttonWidth + 4;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close()).dimensions(x, bottom, buttonWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(heading).formatted(Formatting.BOLD), this.width / 2, 8, Theme.accent());
        int columnWidth = Math.min(200, (this.width - 24) / 2);
        int left = this.width / 2 - columnWidth - 4;
        int rowHeight = this.height < 300 ? 30 : 34;
        for (int i = 0; i < fields.size(); i++) {
            int x = left + (i % 2) * (columnWidth + 8);
            int y = 24 + (i / 2) * rowHeight;
            context.drawTextWithShadow(this.textRenderer, Text.literal(fields.get(i).label()).formatted(Formatting.GRAY), x, y, 0xFFFFFF);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(message).formatted(Formatting.GREEN), this.width / 2, this.height - 36, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
