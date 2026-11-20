package com.minemenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** The menu itself: a grid of toggles on top and a grid of one-shot actions below. */
public class MineMenuScreen extends Screen {
    private static final int COLUMNS = 3;
    private static final int BUTTON_WIDTH = 124;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 2;
    private static final int HEADER_HEIGHT = 12;

    private int togglesHeaderY;
    private int actionsHeaderY;

    public MineMenuScreen() {
        super(Text.literal("MineMenu"));
    }

    @Override
    protected void init() {
        int gridWidth = COLUMNS * BUTTON_WIDTH + (COLUMNS - 1) * GAP;
        int left = (this.width - gridWidth) / 2;

        int toggleCount = Feature.values().length + 2; // + Size + HUD
        int toggleRows = rows(toggleCount);
        int actionRows = rows(ServerCheats.Action.values().length);
        int totalHeight = HEADER_HEIGHT + toggleRows * (BUTTON_HEIGHT + GAP)
                + HEADER_HEIGHT + actionRows * (BUTTON_HEIGHT + GAP) + BUTTON_HEIGHT;
        // Leave room above for the title.
        int top = Math.max(18, (this.height - totalHeight) / 2 + 8);

        // --- Toggles ---
        togglesHeaderY = top;
        int y = top + HEADER_HEIGHT;
        int index = 0;
        for (Feature feature : Feature.values()) {
            ButtonWidget button = ButtonWidget.builder(toggleLabel(feature), b -> {
                        CheatState.toggle(feature);
                        b.setMessage(toggleLabel(feature));
                    })
                    .dimensions(columnX(left, index), rowY(y, index), BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.of(Text.literal(feature.description())))
                    .build();
            addDrawableChild(button);
            index++;
        }
        addDrawableChild(ButtonWidget.builder(sizeLabel(), b -> {
                    CheatState.cycleSize();
                    b.setMessage(sizeLabel());
                })
                .dimensions(columnX(left, index), rowY(y, index), BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.literal("Click to cycle: Normal, Giant (4x) and Tiny (1/4).")))
                .build());
        index++;
        addDrawableChild(ButtonWidget.builder(hudLabel(), b -> {
                    CheatState.toggleHud();
                    b.setMessage(hudLabel());
                })
                .dimensions(columnX(left, index), rowY(y, index), BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.literal("Show the list of active cheats in the top-left corner.")))
                .build());

        // --- Actions ---
        actionsHeaderY = y + toggleRows * (BUTTON_HEIGHT + GAP);
        y = actionsHeaderY + HEADER_HEIGHT;
        index = 0;
        for (ServerCheats.Action action : ServerCheats.Action.values()) {
            addDrawableChild(ButtonWidget.builder(Text.literal(action.displayName()),
                            b -> MineMenuClient.runAction(this.client, action))
                    .dimensions(columnX(left, index), rowY(y, index), BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.of(Text.literal(action.description())))
                    .build());
            index++;
        }

        int doneY = y + actionRows * (BUTTON_HEIGHT + GAP);
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(this.width / 2 - 75, doneY, 150, BUTTON_HEIGHT)
                .build());
    }

    private static int rows(int count) {
        return (count + COLUMNS - 1) / COLUMNS;
    }

    private static int columnX(int left, int index) {
        return left + (index % COLUMNS) * (BUTTON_WIDTH + GAP);
    }

    private static int rowY(int top, int index) {
        return top + (index / COLUMNS) * (BUTTON_HEIGHT + GAP);
    }

    private static Text toggleLabel(Feature feature) {
        return onOff(feature.displayName(), CheatState.isOn(feature));
    }

    private static Text hudLabel() {
        return onOff("Show HUD", CheatState.hudVisible());
    }

    private static Text sizeLabel() {
        CheatState.Size size = CheatState.size();
        return Text.literal("Size: ").append(Text.literal(size.displayName())
                .formatted(size == CheatState.Size.NORMAL ? Formatting.GRAY : Formatting.AQUA));
    }

    private static Text onOff(String name, boolean on) {
        MutableText state = Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED);
        return Text.literal(name + ": ").append(state);
    }

    @Override
    public void tick() {
        // Close if the world was opened to LAN or left while the menu was up.
        if (this.client != null && !MineMenuClient.isSingleplayer(this.client)) {
            close();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("MineMenu").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                this.width / 2, togglesHeaderY - 13, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Toggles").formatted(Formatting.YELLOW), this.width / 2, togglesHeaderY + 1, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Actions").formatted(Formatting.YELLOW), this.width / 2, actionsHeaderY + 1, 0xFFFFFF);
    }

    /** Keep the world running behind the menu so you can watch things happen. */
    @Override
    public boolean shouldPause() {
        return false;
    }
}
