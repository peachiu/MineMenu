package com.mineboost.tools;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Save your graphics settings under a name, load them later, share them as a code. */
public class FpsProfilesScreen extends Screen {
    private static final int WIDTH = 300;
    private final Screen parent;
    private TextFieldWidget name;
    private String message = "";

    public FpsProfilesScreen(Screen parent) {
        super(Text.literal("FPS Profiles"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int y = 24;
        name = new TextFieldWidget(this.textRenderer, left, y, WIDTH / 2 - 2, 20, Text.literal("Name"));
        name.setPlaceholder(Text.literal("Profile name").formatted(Formatting.DARK_GRAY));
        name.setMaxLength(24);
        addDrawableChild(name);
        addDrawableChild(ButtonWidget.builder(Text.literal("Save current settings"), b -> {
            String n = name.getText().isBlank() ? "Profile " + (FpsProfiles.all().size() + 1) : name.getText().trim();
            FpsProfiles.saveCurrent(this.client, n);
            message = "Saved " + n;
            clearAndInit();
        }).dimensions(left + WIDTH / 2 + 2, y, WIDTH / 2 - 2, 20).build());
        y += 26;
        List<String> names = new ArrayList<>(FpsProfiles.all().keySet());
        for (int i = 0; i < Math.min(5, names.size()); i++) {
            String n = names.get(i);
            addDrawableChild(ButtonWidget.builder(Text.literal("Load " + n), b -> {
                FpsProfiles.load(this.client, n);
                message = "Loaded " + n;
            }).dimensions(left, y, WIDTH - 108, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Share"), b -> {
                this.client.keyboard.setClipboard(FpsProfiles.export(n));
                message = "Share code copied";
            }).dimensions(left + WIDTH - 104, y, 52, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Delete").formatted(Formatting.RED), b -> {
                FpsProfiles.delete(n);
                clearAndInit();
            }).dimensions(left + WIDTH - 48, y, 48, 20).build());
            y += 22;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Import from clipboard"), b -> {
            String n = FpsProfiles.importCode(this.client.keyboard.getClipboard());
            message = n == null ? "No MineBoost profile code (MBB1:...) in the clipboard" : "Imported " + n;
            clearAndInit();
        }).dimensions(left, y + 4, WIDTH / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(left + WIDTH / 2 + 2, y + 4, WIDTH / 2 - 2, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("FPS Profiles").formatted(Formatting.GREEN, Formatting.BOLD), this.width / 2, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(message).formatted(Formatting.GREEN), this.width / 2, this.height - 14, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
