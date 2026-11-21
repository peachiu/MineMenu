package com.minemenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** The menu itself: a grid of toggles on top and a grid of one-shot actions below. */
public class MineMenuScreen extends Screen {
    /**
     * Set by MineMeBoost (MineMenu + MineBoost in one mod): builds the tab buttons
     * shown above the menu. Null in the standalone MineMenu mod.
     */
    public static Function<Screen, List<ButtonWidget>> tabs;

    /**
     * Set by MineMeBoost: true while another FPS counter (MineBoost's FPS Overlay) is on,
     * so "Show FPS" can't be turned on as well. Null in the standalone MineMenu mod.
     */
    public static BooleanSupplier otherFpsCounterOn;
    public static String otherFpsCounterReason = "Another FPS counter is on. Turn it off first.";

    private static final int BUTTON_WIDTH = 102;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 2;
    private static final int TAB_ROW_HEIGHT = 24;

    private int headerHeight = 12;
    private boolean hasTabs;

    /** One button in a grid: a label that can change, what clicking does, and a tooltip. */
    private record Spec(Supplier<Text> label, Consumer<ButtonWidget> onClick, String tooltip,
                        BooleanSupplier locked, String lockedTooltip) {
        Spec(Supplier<Text> label, Consumer<ButtonWidget> onClick, String tooltip) {
            this(label, onClick, tooltip, () -> false, null);
        }
    }

    private int togglesHeaderY;
    private int actionsHeaderY;

    public MineMenuScreen() {
        super(Text.literal("MineMenu"));
    }

    private List<Spec> toggleSpecs() {
        List<Spec> specs = new ArrayList<>();
        for (Feature feature : Feature.values()) {
            specs.add(new Spec(() -> onOff(feature.displayName(), CheatState.isOn(feature)),
                    b -> CheatState.toggle(feature), feature.description()));
        }
        specs.add(new Spec(MineMenuScreen::sizeLabel, b -> CheatState.cycleSize(),
                "Click to cycle: Normal, Giant (4x) and Tiny (1/4)."));
        specs.add(new Spec(() -> onOff("Show HUD", CheatState.hudVisible()), b -> CheatState.toggleHud(),
                "Show the list of active cheats in the top-left corner."));
        specs.add(new Spec(() -> onOff("Show FPS", CheatState.fpsVisible()), b -> CheatState.toggleFps(),
                "Show your frames per second in the top-left corner.",
                // Only blocks turning it ON: if both are somehow on, you can still turn this one off.
                () -> otherFpsCounterOn != null && otherFpsCounterOn.getAsBoolean() && !CheatState.fpsVisible(),
                otherFpsCounterReason));
        return specs;
    }

    private List<Spec> actionSpecs() {
        List<Spec> specs = new ArrayList<>();
        specs.add(new Spec(() -> Text.literal("Spawn Mob...").formatted(Formatting.AQUA),
                b -> this.client.setScreen(new MobSpawnScreen(this)),
                "Pick any mob and spawn it where you're looking. Last picked: "
                        + Registries.ENTITY_TYPE.get(CheatState.selectedMob()).getName().getString() + "."));
        specs.add(new Spec(() -> Text.literal("Give XP...").formatted(Formatting.AQUA),
                b -> this.client.setScreen(new XpScreen(this)),
                "Type a number of levels and get that much XP, e.g. 64."));
        for (ServerCheats.Action action : ServerCheats.Action.values()) {
            specs.add(new Spec(() -> Text.literal(action.displayName()),
                    b -> MineMenuClient.runAction(this.client, action), action.description()));
        }
        return specs;
    }

    @Override
    protected void init() {
        List<Spec> toggles = toggleSpecs();
        List<Spec> actions = actionSpecs();

        List<ButtonWidget> tabButtons = tabs != null ? tabs.apply(this) : List.of();
        hasTabs = !tabButtons.isEmpty();
        if (hasTabs) {
            // The tabs replace the title, so squeeze the section labels a little to fit small screens.
            headerHeight = 10;
            int rowWidth = tabButtons.stream().mapToInt(ButtonWidget::getWidth).sum() + 4 * (tabButtons.size() - 1);
            int x = (this.width - rowWidth) / 2;
            for (ButtonWidget tab : tabButtons) {
                tab.setPosition(x, 2);
                addDrawableChild(tab);
                x += tab.getWidth() + 4;
            }
        }

        // Use 4 columns when they fit, otherwise 3.
        int columns = this.width >= 4 * BUTTON_WIDTH + 3 * GAP + 8 ? 4 : 3;
        int gridWidth = columns * BUTTON_WIDTH + (columns - 1) * GAP;
        int left = (this.width - gridWidth) / 2;

        int toggleRows = rows(toggles.size(), columns);
        int actionRows = rows(actions.size(), columns);
        int totalHeight = headerHeight + toggleRows * (BUTTON_HEIGHT + GAP)
                + headerHeight + actionRows * (BUTTON_HEIGHT + GAP) + BUTTON_HEIGHT;
        // Leave room above for the title (or the tabs).
        int minTop = hasTabs ? TAB_ROW_HEIGHT : 18;
        int top = Math.max(minTop, (this.height - totalHeight) / 2 + (hasTabs ? TAB_ROW_HEIGHT / 2 : 8));

        togglesHeaderY = top;
        int y = addGrid(toggles, left, top + headerHeight, columns);
        actionsHeaderY = y;
        y = addGrid(actions, left, y + headerHeight, columns);

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(this.width / 2 - 75, y, 150, BUTTON_HEIGHT)
                .build());
    }

    /** Adds a grid of buttons and returns the y just below it. */
    private int addGrid(List<Spec> specs, int left, int top, int columns) {
        for (int i = 0; i < specs.size(); i++) {
            Spec spec = specs.get(i);
            int x = left + (i % columns) * (BUTTON_WIDTH + GAP);
            int y = top + (i / columns) * (BUTTON_HEIGHT + GAP);
            boolean locked = spec.locked().getAsBoolean();
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(spec.label().get(), b -> {
                        spec.onClick().accept(b);
                        b.setMessage(spec.label().get());
                    })
                    .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.of(Text.literal(locked ? spec.lockedTooltip() : spec.tooltip())))
                    .build());
            button.active = !locked;
        }
        return top + rows(specs.size(), columns) * (BUTTON_HEIGHT + GAP);
    }

    private static int rows(int count, int columns) {
        return (count + columns - 1) / columns;
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
        if (!hasTabs) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("MineMenu").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                    this.width / 2, togglesHeaderY - 13, 0xFFFFFF);
        }
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
