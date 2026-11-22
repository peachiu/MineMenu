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

/** The menu itself: category pages (Player, Combat, World, Items), each with toggles and actions. */
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

    private final int headerHeight = 12;
    private boolean hasTabs;

    /** One button in a grid: a label that can change, what clicking does, and a tooltip. */
    private record Spec(Supplier<Text> label, Consumer<ButtonWidget> onClick, String tooltip,
                        BooleanSupplier locked, String lockedTooltip) {
        Spec(Supplier<Text> label, Consumer<ButtonWidget> onClick, String tooltip) {
            this(label, onClick, tooltip, () -> false, null);
        }
    }

    private int togglesHeaderY = -1;
    private int actionsHeaderY = -1;
    private int titleY;

    /** The page you were on last; the menu reopens there. */
    private static Category currentCategory = Category.PLAYER;

    public MineMenuScreen() {
        super(Text.literal("MineMenu"));
    }

    private List<Spec> toggleSpecs(Category category) {
        List<Spec> specs = new ArrayList<>();
        for (Feature feature : Feature.values()) {
            if (feature.category() == category) {
                specs.add(new Spec(() -> onOff(feature.displayName(), CheatState.isOn(feature)),
                        b -> CheatState.toggle(feature), feature.description()));
            }
        }
        if (category == Category.PLAYER) {
            specs.add(new Spec(MineMenuScreen::sizeLabel, b -> CheatState.cycleSize(),
                    "Click to cycle: Normal, Giant (4x) and Tiny (1/4)."));
        }
        return specs;
    }

    private List<Spec> actionSpecs(Category category) {
        List<Spec> specs = new ArrayList<>();
        if (category == Category.WORLD) {
            specs.add(new Spec(() -> Text.literal("Spawn Mob...").formatted(Formatting.AQUA),
                    b -> this.client.setScreen(new MobSpawnScreen(this)),
                    "Pick any mob and spawn it where you're looking. Last picked: "
                            + Registries.ENTITY_TYPE.get(CheatState.selectedMob()).getName().getString() + "."));
        }
        if (category == Category.ITEMS) {
            specs.add(new Spec(() -> Text.literal("Give Item...").formatted(Formatting.AQUA),
                    b -> this.client.setScreen(new ItemGiveScreen(this)),
                    "Pick any item and get it in your inventory, or dropped on the ground where you're looking."));
            specs.add(new Spec(() -> Text.literal("Delete Items...").formatted(Formatting.RED),
                    b -> DeleteMode.open(this.client),
                    "Opens your inventory in delete mode: right-click any item to delete it. "
                            + "Undo Delete gives it back. Closing the inventory ends delete mode."));
            specs.add(new Spec(() -> Text.literal("Give XP...").formatted(Formatting.AQUA),
                    b -> this.client.setScreen(new XpScreen(this)),
                    "Type a number of levels and get that much XP, e.g. 64."));
        }
        for (ServerCheats.Action action : ServerCheats.Action.values()) {
            if (action.category() == category) {
                specs.add(new Spec(() -> Text.literal(action.displayName()),
                        b -> MineMenuClient.runAction(this.client, action), action.description()));
            }
        }
        return specs;
    }

    @Override
    protected void init() {
        List<ButtonWidget> tabButtons = tabs != null ? tabs.apply(this) : List.of();
        hasTabs = !tabButtons.isEmpty();
        int y = 2;
        if (hasTabs) {
            y = addCenteredRow(tabButtons, y) + 4;
        } else {
            titleY = 5;
            y = 16;
        }

        // Category pages: Player, Combat, World, Items.
        List<ButtonWidget> pageButtons = new ArrayList<>();
        for (Category category : Category.values()) {
            ButtonWidget page = ButtonWidget.builder(Text.literal(category.displayName()).formatted(category.color()),
                            b -> {
                                currentCategory = category;
                                clearAndInit();
                            })
                    .width(90)
                    .build();
            page.active = category != currentCategory; // the open page looks pressed
            pageButtons.add(page);
        }
        y = addCenteredRow(pageButtons, y) + 4;

        // Use 4 columns when they fit, otherwise 3.
        int columns = this.width >= 4 * BUTTON_WIDTH + 3 * GAP + 8 ? 4 : 3;
        int gridWidth = columns * BUTTON_WIDTH + (columns - 1) * GAP;
        int left = (this.width - gridWidth) / 2;

        List<Spec> toggles = toggleSpecs(currentCategory);
        List<Spec> actions = actionSpecs(currentCategory);
        togglesHeaderY = -1;
        actionsHeaderY = -1;
        if (!toggles.isEmpty()) {
            togglesHeaderY = y;
            y = addGrid(toggles, left, y + headerHeight, columns) + 2;
        }
        if (!actions.isEmpty()) {
            actionsHeaderY = y;
            addGrid(actions, left, y + headerHeight, columns);
        }

        // Always at the bottom: Show HUD, Enable/Disable All, Done, Show FPS.
        int bottom = this.height - BUTTON_HEIGHT - 4;
        int w = 100;
        int rowLeft = (this.width - (w * 4 + 12)) / 2;
        addGrid(List.of(new Spec(() -> onOff("Show HUD", CheatState.hudVisible()), b -> CheatState.toggleHud(),
                "Show the list of active cheats in the top-left corner.")), rowLeft, bottom, 1, w);
        addDrawableChild(ButtonWidget.builder(allLabel(), b -> {
                    boolean turnOn = !CheatState.allOn();
                    CheatState.setAll(turnOn);
                    clearAndInit(); // refresh every ON/OFF label on the page
                })
                .dimensions(rowLeft + (w + 4), bottom, w, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.literal("Turn every ON/OFF hack on at once (on all four pages). "
                        + "Click again to turn them all off. Show HUD, Show FPS and Size aren't changed.")))
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(rowLeft + 2 * (w + 4), bottom, w, BUTTON_HEIGHT)
                .build());
        addGrid(List.of(new Spec(() -> onOff("Show FPS", CheatState.fpsVisible()), b -> CheatState.toggleFps(),
                "Show your frames per second in the top-left corner.",
                // Only blocks turning it ON: if both are somehow on, you can still turn this one off.
                () -> otherFpsCounterOn != null && otherFpsCounterOn.getAsBoolean() && !CheatState.fpsVisible(),
                otherFpsCounterReason)), rowLeft + 3 * (w + 4), bottom, 1, w);
    }

    /** "Enable All" until every hack is on, then "Disable All". */
    private static Text allLabel() {
        return CheatState.allOn()
                ? Text.literal("Disable All").formatted(Formatting.RED)
                : Text.literal("Enable All").formatted(Formatting.GREEN);
    }

    /** Adds buttons in a centred row at {@code y} and returns the y just below it. */
    private int addCenteredRow(List<ButtonWidget> buttons, int y) {
        int rowWidth = buttons.stream().mapToInt(ButtonWidget::getWidth).sum() + 4 * (buttons.size() - 1);
        int x = (this.width - rowWidth) / 2;
        for (ButtonWidget button : buttons) {
            button.setPosition(x, y);
            addDrawableChild(button);
            x += button.getWidth() + 4;
        }
        return y + BUTTON_HEIGHT;
    }

    /** Adds a grid of buttons and returns the y just below it. */
    private int addGrid(List<Spec> specs, int left, int top, int columns) {
        return addGrid(specs, left, top, columns, BUTTON_WIDTH);
    }

    private int addGrid(List<Spec> specs, int left, int top, int columns, int buttonWidth) {
        for (int i = 0; i < specs.size(); i++) {
            Spec spec = specs.get(i);
            int x = left + (i % columns) * (buttonWidth + GAP);
            int y = top + (i / columns) * (BUTTON_HEIGHT + GAP);
            boolean locked = spec.locked().getAsBoolean();
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(spec.label().get(), b -> {
                        spec.onClick().accept(b);
                        b.setMessage(spec.label().get());
                    })
                    .dimensions(x, y, buttonWidth, BUTTON_HEIGHT)
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
                    this.width / 2, titleY, 0xFFFFFF);
        }
        if (togglesHeaderY >= 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Toggles").formatted(Formatting.YELLOW), this.width / 2, togglesHeaderY + 1, 0xFFFFFF);
        }
        if (actionsHeaderY >= 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Actions").formatted(Formatting.YELLOW), this.width / 2, actionsHeaderY + 1, 0xFFFFFF);
        }
    }

    /** Keep the world running behind the menu so you can watch things happen. */
    @Override
    public boolean shouldPause() {
        return false;
    }
}
