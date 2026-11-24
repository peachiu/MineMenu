package com.minemenu.ui;

import com.minemenu.core.CheatConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Command Palette (Ctrl+K): type a few letters, pick with the arrow keys or the mouse, Enter runs it.
 * Searches every hack, action, screen, FPS preset, waypoint and macro by name.
 */
public class CommandPalette extends Screen {
    private static final int WIDTH = 320;
    private static final int ROW = 13;
    private final @Nullable Screen parent;
    private TextFieldWidget field;
    private List<MenuEntry> results = List.of();
    private int selected;

    public CommandPalette(@Nullable Screen parent) {
        super(Text.literal("Command Palette"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String text = field != null ? field.getText() : "";
        field = new TextFieldWidget(this.textRenderer, (this.width - WIDTH) / 2, 30, WIDTH, 20, Text.literal("Search"));
        field.setPlaceholder(Text.literal("Type a hack, action, preset or waypoint...").formatted(Formatting.DARK_GRAY));
        field.setMaxLength(64);
        field.setText(text);
        field.setChangedListener(t -> refresh());
        addDrawableChild(field);
        setInitialFocus(field);
        refresh();
    }

    private void refresh() {
        String query = field.getText().trim().toLowerCase(Locale.ROOT);
        List<MenuEntry> found = new ArrayList<>();
        for (MenuEntry entry : MenuEntries.all()) {
            if (entry.matches(query)) {
                found.add(entry);
            }
        }
        // Names that start with what you typed first, then favorites, then the rest.
        found.sort(Comparator.<MenuEntry>comparingInt(e -> e.name().toLowerCase(Locale.ROOT).startsWith(query) ? 0 : 1)
                .thenComparingInt(e -> CheatConfig.get().isFavorite(e.id()) ? 0 : 1));
        int maxRows = Math.max(3, (this.height - 70) / ROW);
        results = found.subList(0, Math.min(maxRows, found.size()));
        selected = Math.min(selected, Math.max(0, results.size() - 1));
    }

    private void runSelected() {
        if (selected < 0 || selected >= results.size()) {
            return;
        }
        MenuEntry entry = results.get(selected);
        this.client.setScreen(parent);
        MenuEntries.run(this.client, parent, entry);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            selected = Math.min(results.size() - 1, selected + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            selected = Math.max(0, selected - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            runSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (this.width - WIDTH) / 2;
        int index = (int) ((mouseY - 56) / ROW);
        if (mouseX >= left && mouseX <= left + WIDTH && mouseY >= 56 && index >= 0 && index < results.size()) {
            selected = index;
            runSelected();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int left = (this.width - WIDTH) / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Command Palette").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                this.width / 2, 14, 0xFFFFFF);
        int y = 56;
        for (int i = 0; i < results.size(); i++) {
            MenuEntry entry = results.get(i);
            boolean hover = mouseX >= left && mouseX <= left + WIDTH && mouseY >= y && mouseY < y + ROW;
            if (i == selected || hover) {
                context.fill(left - 2, y - 2, left + WIDTH + 2, y + ROW - 2, i == selected ? 0x80FF55FF : 0x40FFFFFF);
            }
            String kind = switch (entry.kind()) {
                case HACK -> entry.isOn() != null && entry.isOn().getAsBoolean() ? "ON " : "OFF";
                case ACTION -> "run";
                case SCREEN -> "open";
                case PRESET -> "FPS";
                case WAYPOINT -> "go";
                case MACRO -> "macro";
            };
            int kindColor = kind.equals("ON ") ? 0x55FF55 : kind.equals("OFF") ? 0xFF5555 : 0xAAAAAA;
            context.drawTextWithShadow(this.textRenderer, Text.literal(kind), left, y, kindColor);
            Text name = Text.literal((CheatConfig.get().isFavorite(entry.id()) ? "★ " : "") + entry.name());
            context.drawTextWithShadow(this.textRenderer, name, left + 34, y, 0xFFFFFF);
            if (entry.category() != null) {
                Text cat = Text.literal(entry.category().displayName()).formatted(entry.category().color());
                context.drawTextWithShadow(this.textRenderer, cat, left + WIDTH - this.textRenderer.getWidth(cat), y, 0xFFFFFF);
            }
            y += ROW;
        }
        if (results.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Nothing found").formatted(Formatting.GRAY), this.width / 2, 60, 0xFFFFFF);
        } else if (selected >= 0 && selected < results.size()) {
            String description = results.get(selected).description();
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(description.length() > 90 ? description.substring(0, 88) + "..." : description)
                    .formatted(Formatting.GRAY), this.width / 2, this.height - 14, 0xFFFFFF);
        }
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
