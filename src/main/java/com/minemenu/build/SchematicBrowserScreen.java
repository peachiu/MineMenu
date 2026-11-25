package com.minemenu.build;

import com.minemenu.ui.MenuEntries;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Schematics browser: the saved .nbt files on the left (with a search box), and for the selected one its size,
 * block count and two pictures (from above and from the front). Load & paste starts the usual paste preview;
 * Rename and Delete work on the file (Delete asks for a second click). Fits 427x240 GUI pixels (854x480 at GUI 2).
 */
public class SchematicBrowserScreen extends Screen {
    private static final int ROW = 18;

    private final Screen parent;
    private final Map<String, SchematicPreview> previews = new HashMap<>();
    private final Map<String, String> errors = new HashMap<>();
    private List<String> names = new ArrayList<>();
    private List<String> shown = new ArrayList<>();
    private String selected;
    private int scroll;
    private boolean confirmDelete;
    private TextFieldWidget search;
    private int listWidth;
    private int listTop;
    private int listRows;

    public SchematicBrowserScreen(Screen parent) {
        super(Text.translatable("minemenu.schematics.title"));
        this.parent = parent;
    }

    /** For tests: the names in the list right now. */
    public List<String> shownNames() {
        return shown;
    }

    public String selected() {
        return selected;
    }

    public SchematicPreview preview() {
        return selected == null ? null : previews.get(selected);
    }

    @Override
    protected void init() {
        names = BuildTools.listSchematics();
        listWidth = Math.max(120, this.width * 2 / 5);
        listTop = 48;
        int bottom = this.height - 30;
        listRows = Math.max(1, (bottom - listTop) / ROW);
        String typed = search == null ? "" : search.getText();
        search = new TextFieldWidget(this.textRenderer, 8, 24, listWidth - 8, 18, Text.translatable("minemenu.schematics.search"));
        search.setPlaceholder(Text.translatable("minemenu.schematics.search").formatted(Formatting.DARK_GRAY));
        search.setText(typed);
        search.setChangedListener(t -> rebuild());
        addDrawableChild(search);
        if (selected == null && !names.isEmpty()) {
            selected = names.get(0);
        }
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        addDrawableChild(search);
        String filter = search.getText().toLowerCase(Locale.ROOT).trim();
        shown = new ArrayList<>();
        for (String name : names) {
            if (filter.isEmpty() || name.toLowerCase(Locale.ROOT).contains(filter)) {
                shown.add(name);
            }
        }
        scroll = Math.max(0, Math.min(scroll, shown.size() - listRows));
        for (int i = scroll; i < Math.min(shown.size(), scroll + listRows); i++) {
            String name = shown.get(i);
            Text label = Text.literal(name).formatted(name.equals(selected) ? Formatting.YELLOW : Formatting.WHITE);
            addDrawableChild(ButtonWidget.builder(label, b -> select(name))
                    .dimensions(8, listTop + (i - scroll) * ROW, listWidth - 8, ROW - 2).build());
        }
        // Bottom row: what you can do with the selected one.
        int y = this.height - 26;
        int x = 8;
        int w = Math.max(50, (this.width - 16 - 5 * 4) / 6);
        ButtonWidget load = addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.schematics.load").formatted(Formatting.GREEN), b -> load())
                .dimensions(x, y, w, 20).build());
        load.active = selected != null;
        x += w + 4;
        ButtonWidget rename = addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.schematics.rename"), b -> rename())
                .dimensions(x, y, w, 20).build());
        rename.active = selected != null;
        x += w + 4;
        ButtonWidget delete = addDrawableChild(ButtonWidget.builder(Text.translatable(confirmDelete ? "minemenu.schematics.delete_confirm" : "minemenu.schematics.delete")
                .formatted(Formatting.RED), b -> delete()).dimensions(x, y, w, 20).build());
        delete.active = selected != null;
        x += w + 4;
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.schematics.save_copy"), b -> saveCopy()).dimensions(x, y, w, 20).build());
        x += w + 4;
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.schematics.folder"), b -> Util.getOperatingSystem().open(BuildTools.SCHEMATICS))
                .dimensions(x, y, w, 20).build());
        x += w + 4;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), b -> close()).dimensions(x, y, w, 20).build());
    }

    public void select(String name) {
        selected = name;
        confirmDelete = false;
        if (!previews.containsKey(name) && !errors.containsKey(name)) {
            try {
                previews.put(name, SchematicPreview.load(BuildTools.schematicFile(name)));
            } catch (Exception e) {
                errors.put(name, String.valueOf(e.getMessage()));
            }
        }
        rebuild();
    }

    private void load() {
        if (selected == null) {
            return;
        }
        String name = selected;
        this.client.setScreen(null);
        MenuEntries.onServer(p -> BuildTools.loadSchematic(p, name)).accept(MinecraftClient.getInstance(), null);
    }

    private void rename() {
        String old = selected;
        this.client.setScreen(new NameScreen(this, Text.translatable("minemenu.schematics.new_name").getString(), name -> {
            try {
                Files.move(BuildTools.schematicFile(old), BuildTools.schematicFile(name));
                previews.remove(old);
                selected = BuildTools.schematicFile(name).getFileName().toString().replace(".nbt", "");
            } catch (Exception e) {
                errors.put(old, String.valueOf(e.getMessage()));
            }
        }));
    }

    private void delete() {
        if (!confirmDelete) {
            confirmDelete = true;
            rebuild();
            return;
        }
        try {
            Files.deleteIfExists(BuildTools.schematicFile(selected));
        } catch (Exception e) {
            errors.put(selected, String.valueOf(e.getMessage()));
        }
        previews.remove(selected);
        selected = null;
        confirmDelete = false;
        clearAndInit();
    }

    private void saveCopy() {
        this.client.setScreen(new NameScreen(this, Text.translatable("minemenu.schematics.new_name").getString(),
                name -> MenuEntries.onServer(p -> BuildTools.saveSchematic(p, name)).accept(MinecraftClient.getInstance(), null)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX < listWidth) {
            scroll = Math.max(0, Math.min(Math.max(0, shown.size() - listRows), scroll - (int) Math.signum(vertical)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title.copy().formatted(Formatting.AQUA, Formatting.BOLD), this.width / 2, 8, 0xFFFFFF);
        if (names.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.translatable("minemenu.schematics.empty").formatted(Formatting.GRAY), 10, listTop + 4, 0xFFFFFF);
        }
        int px = listWidth + 10;
        int pw = this.width - px - 8;
        int py = 26;
        if (selected == null) {
            return;
        }
        if (errors.containsKey(selected)) {
            context.drawTextWithShadow(this.textRenderer, Text.translatable("minemenu.schematics.unreadable", errors.get(selected)).formatted(Formatting.RED), px, py, 0xFFFFFF);
            return;
        }
        SchematicPreview p = previews.get(selected);
        if (p == null) {
            return;
        }
        context.drawTextWithShadow(this.textRenderer, Text.literal(selected).formatted(Formatting.YELLOW), px, py, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("minemenu.schematics.info", p.size.getX(), p.size.getY(), p.size.getZ(), p.blocks,
                Math.max(1, p.fileBytes / 1024)).formatted(Formatting.GRAY), px, py + 11, 0xFFFFFF);
        // Two pictures side by side (or one above the other in a narrow window).
        int boxTop = py + 26;
        int boxHeight = this.height - 34 - boxTop;
        boolean sideBySide = pw >= boxHeight * 2;
        int boxWidth = sideBySide ? (pw - 6) / 2 : pw;
        int each = sideBySide ? boxHeight : (boxHeight - 14) / 2;
        drawPicture(context, Text.translatable("minemenu.schematics.top_view"), p.top, p.topWidth, p.topHeight, px, boxTop, boxWidth, each);
        if (sideBySide) {
            drawPicture(context, Text.translatable("minemenu.schematics.front_view"), p.front, p.frontWidth, p.frontHeight, px + boxWidth + 6, boxTop, boxWidth, each);
        } else {
            drawPicture(context, Text.translatable("minemenu.schematics.front_view"), p.front, p.frontWidth, p.frontHeight, px, boxTop + each + 14, boxWidth, each);
        }
    }

    private void drawPicture(DrawContext context, Text label, int[] colors, int w, int h, int x, int y, int boxW, int boxH) {
        context.drawTextWithShadow(this.textRenderer, label.copy().formatted(Formatting.GRAY), x, y, 0xFFFFFF);
        int top = y + 10;
        int areaH = boxH - 10;
        context.fill(x, top, x + boxW, top + areaH, 0x60000000);
        if (w <= 0 || h <= 0 || areaH <= 4) {
            return;
        }
        float cell = Math.min((float) boxW / w, (float) areaH / h);
        int ox = x + (int) ((boxW - cell * w) / 2);
        int oy = top + (int) ((areaH - cell * h) / 2);
        for (int cy = 0; cy < h; cy++) {
            for (int cx = 0; cx < w; cx++) {
                int color = colors[cy * w + cx];
                if (color != 0) {
                    int x0 = ox + (int) (cx * cell), y0 = oy + (int) (cy * cell);
                    context.fill(x0, y0, Math.max(x0 + 1, ox + (int) ((cx + 1) * cell)), Math.max(y0 + 1, oy + (int) ((cy + 1) * cell)), color);
                }
            }
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
