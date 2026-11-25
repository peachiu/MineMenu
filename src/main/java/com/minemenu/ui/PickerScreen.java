package com.minemenu.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** A searchable list of choices in a grid (schematics, biomes, mobs, structures...). Click one to pick it. */
public class PickerScreen extends Screen {
    public record Choice(String label, String id) {
    }

    private final Screen parent;
    private final String heading;
    private final List<Choice> choices;
    private final Consumer<Choice> onPick;
    private final List<ButtonWidget> extra;
    private TextFieldWidget search;
    private String query = "";
    private int page;
    private final List<ButtonWidget> grid = new ArrayList<>();

    public PickerScreen(Screen parent, String heading, List<Choice> choices, Consumer<Choice> onPick, List<ButtonWidget> extra) {
        super(Text.translatable(heading));
        this.parent = parent;
        this.heading = heading;
        this.choices = choices;
        this.onPick = onPick;
        this.extra = extra;
    }

    @Override
    protected void init() {
        search = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 22, 200, 18, Text.translatable("minemenu.text.search"));
        search.setPlaceholder(Text.translatable("minemenu.text.search_2").formatted(Formatting.DARK_GRAY));
        search.setText(query);
        search.setChangedListener(text -> {
            query = text;
            page = 0;
            rebuild();
        });
        addDrawableChild(search);
        int y = this.height - 24;
        int x = this.width / 2 - (extra.size() + 1) * 52;
        for (ButtonWidget button : extra) {
            button.setDimensionsAndPosition(100, 20, x, y);
            addDrawableChild(button);
            x += 104;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.back"), b -> close()).dimensions(x, y, 100, 20).build());
        rebuild();
        setInitialFocus(search);
    }

    private void rebuild() {
        grid.forEach(this::remove);
        grid.clear();
        String q = query.toLowerCase(Locale.ROOT);
        List<Choice> shown = new ArrayList<>();
        for (Choice choice : choices) {
            if (q.isBlank() || choice.label().toLowerCase(Locale.ROOT).contains(q) || choice.id().toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(choice);
            }
        }
        int columns = Math.max(1, Math.min(4, (this.width - 20) / 124));
        int rows = Math.max(1, (this.height - 44 - 30) / 22);
        int perPage = columns * rows;
        int pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int left = (this.width - columns * 124 + 4) / 2;
        for (int i = 0; i < perPage && page * perPage + i < shown.size(); i++) {
            Choice choice = shown.get(page * perPage + i);
            String label = choice.label();
            while (this.textRenderer.getWidth(label) > 112 && label.length() > 4) {
                label = label.substring(0, label.length() - 2);
            }
            grid.add(addDrawableChild(ButtonWidget.builder(Text.translatable(label), b -> onPick.accept(choice))
                    .dimensions(left + (i % columns) * 124, 44 + (i / columns) * 22, 120, 20).build()));
        }
        if (pages > 1) {
            ButtonWidget prev = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
                page--;
                rebuild();
            }).dimensions(this.width / 2 - 126, 21, 20, 20).build());
            prev.active = page > 0;
            ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
                page++;
                rebuild();
            }).dimensions(this.width / 2 + 106, 21, 20, 20).build());
            next.active = page < pages - 1;
            grid.add(prev);
            grid.add(next);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable(heading).formatted(Formatting.BOLD), this.width / 2, 8, Theme.accent());
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
