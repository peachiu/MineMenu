package com.minemenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/** Type a number of XP levels, then set your level to it or add it on top. */
public class XpScreen extends Screen {
    private static final int PANEL_WIDTH = 200;

    private final Screen parent;
    private TextFieldWidget levelsField;
    private ButtonWidget setButton;
    private ButtonWidget addButton;

    public XpScreen(Screen parent) {
        super(Text.literal("Give XP"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int y = this.height / 2 - 30;

        String previous = levelsField != null ? levelsField.getText() : "";
        levelsField = new TextFieldWidget(this.textRenderer, left, y, PANEL_WIDTH, 20, Text.literal("Levels"));
        levelsField.setMaxLength(6);
        levelsField.setTextPredicate(text -> text.chars().allMatch(Character::isDigit));
        levelsField.setPlaceholder(Text.literal("Levels, e.g. 64").formatted(Formatting.DARK_GRAY));
        levelsField.setText(previous);
        levelsField.setChangedListener(text -> updateButtons());
        addDrawableChild(levelsField);

        int buttonWidth = (PANEL_WIDTH - 4) / 2;
        y += 26;
        setButton = addDrawableChild(ButtonWidget.builder(Text.literal("Set Level").formatted(Formatting.GREEN), b -> give(true))
                .dimensions(left, y, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.literal("Your level becomes exactly this number. Enter does this too.")))
                .build());
        addButton = addDrawableChild(ButtonWidget.builder(Text.literal("Add Levels"), b -> give(false))
                .dimensions(left + buttonWidth + 4, y, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.literal("Add this many levels on top of what you have.")))
                .build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset to 0"), b -> {
                    levelsField.setText("0");
                    give(true);
                })
                .dimensions(left, y, buttonWidth, 20)
                .tooltip(Tooltip.of(Text.literal("Remove all your XP.")))
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(left + buttonWidth + 4, y, buttonWidth, 20)
                .build());

        updateButtons();
        setInitialFocus(levelsField);
    }

    /** The typed number, capped at {@link ServerCheats#MAX_XP_LEVELS}, or -1 if the box is empty. */
    private int typedLevels() {
        String text = levelsField.getText();
        if (text.isEmpty()) {
            return -1;
        }
        try {
            return Math.min(Integer.parseInt(text), ServerCheats.MAX_XP_LEVELS);
        } catch (NumberFormatException e) {
            return ServerCheats.MAX_XP_LEVELS;
        }
    }

    private void updateButtons() {
        boolean hasNumber = typedLevels() >= 0;
        if (setButton != null) {
            setButton.active = hasNumber;
        }
        if (addButton != null) {
            addButton.active = hasNumber;
        }
    }

    private void give(boolean set) {
        int levels = typedLevels();
        IntegratedServer server = this.client != null ? this.client.getServer() : null;
        if (levels < 0 || server == null) {
            return;
        }
        server.execute(() -> ServerCheats.giveXp(server, levels, set));
        this.client.setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && typedLevels() >= 0) {
            give(true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        if (this.client != null && !MineMenuClient.isSingleplayer(this.client)) {
            this.client.setScreen(null);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int top = this.height / 2 - 30;
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Give XP").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                this.width / 2, top - 30, 0xFFFFFF);
        if (this.client != null && this.client.player != null) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("You have " + this.client.player.experienceLevel + " levels. How many do you want?")
                            .formatted(Formatting.GRAY),
                    this.width / 2, top - 14, 0xFFFFFF);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
