package com.minemenu;

import com.minemenu.core.CheatConfig;
import com.minemenu.core.FeatureSettings;
import com.minemenu.ui.CommandPalette;
import com.minemenu.ui.FeatureScreen;
import com.minemenu.ui.MenuEntries;
import com.minemenu.ui.MenuEntry;
import com.minemenu.ui.MenuSettingsScreen;
import com.minemenu.ui.Theme;
import com.minemenu.ui.WhatsNewScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * The menu: pages (Favorites, Player, Combat, World, Items, Build, Fun, Utility), a search box, and a
 * scrollable grid of hacks (ON/OFF), screens (...) and actions. Right-click any button for its settings,
 * a key and the favorite star. Since 3.4 it also opens on servers, with only the fair Utility hacks usable.
 */
public class MineMenuScreen extends Screen {
    /** Set by MineMeBoost: the MineMenu / MineBoost tab buttons. Null in the standalone MineMenu mod. */
    public static Function<Screen, List<ButtonWidget>> tabs;
    /** Set by MineMeBoost: true while MineBoost's FPS Overlay is on, so "Show FPS" can't be turned on too. */
    public static BooleanSupplier otherFpsCounterOn;
    public static String otherFpsCounterReason = "Another FPS counter is on. Turn it off first.";

    private static final int BUTTON_WIDTH = 102;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 2;

    /** The page you were on, the search text and the scroll position: the menu reopens the same way. */
    private static Category currentCategory = Category.PLAYER;
    private static boolean favoritesPage;
    private static String search = "";
    private static int scroll;

    private boolean hasTabs;
    private int titleY;
    private int gridTop;
    private int gridBottom;
    private int contentHeight;
    private long openedAt;
    private TextFieldWidget searchField;

    public MineMenuScreen() {
        super(Text.literal("MineMenu"));
    }

    private boolean singleplayer() {
        return this.client != null && MineMenuClient.isSingleplayer(this.client);
    }

    /** What the grid shows: search results, favorites, or the current page. */
    private List<MenuEntry> entries() {
        List<MenuEntry> list = new ArrayList<>();
        if (!search.isBlank()) {
            for (MenuEntry entry : MenuEntries.all()) {
                if (entry.matches(search)) {
                    list.add(entry);
                }
            }
        } else if (favoritesPage) {
            for (String id : CheatConfig.get().favorites) {
                MenuEntry entry = MenuEntries.byId(id);
                if (entry != null) {
                    list.add(entry);
                }
            }
        } else {
            list.addAll(MenuEntries.page(currentCategory));
        }
        return list;
    }

    @Override
    protected void init() {
        if (openedAt == 0) {
            openedAt = Util.getMeasuringTimeMs();
        }
        List<ButtonWidget> tabButtons = tabs != null ? tabs.apply(this) : List.of();
        hasTabs = !tabButtons.isEmpty();
        int y = 2;
        if (hasTabs) {
            y = addCenteredRow(tabButtons, y) + 3;
        } else {
            titleY = 4;
            y = 14;
        }

        // Pages: Favorites, then every category.
        List<ButtonWidget> pages = new ArrayList<>();
        int pageWidth = Math.min(72, (this.width - 16 - 4 * Category.values().length) / (Category.values().length + 1));
        ButtonWidget favorites = ButtonWidget.builder(Text.literal("★").formatted(Formatting.YELLOW), b -> {
                    favoritesPage = true;
                    search = "";
                    scroll = 0;
                    clearAndInit();
                })
                .width(Math.max(20, pageWidth / 2))
                .tooltip(Tooltip.of(Text.literal("Favorites: right-click any button and star it to put it here.")))
                .build();
        favorites.active = !favoritesPage || !search.isBlank();
        pages.add(favorites);
        for (Category category : Category.values()) {
            ButtonWidget page = ButtonWidget.builder(Text.literal(category.displayName()).formatted(category.color()), b -> {
                        currentCategory = category;
                        favoritesPage = false;
                        search = "";
                        scroll = 0;
                        clearAndInit();
                    })
                    .width(pageWidth)
                    .build();
            page.active = favoritesPage || category != currentCategory || !search.isBlank();
            pages.add(page);
        }
        y = addCenteredRow(pages, y) + 3;

        // Search, settings and palette.
        int searchWidth = Math.min(220, this.width - 120);
        int rowLeft = (this.width - (searchWidth + 4 + 20 + 4 + 50)) / 2;
        searchField = new TextFieldWidget(this.textRenderer, rowLeft, y, searchWidth, 18, Text.literal("Search"));
        searchField.setPlaceholder(Text.literal("Search all hacks... (Ctrl+K anywhere)").formatted(Formatting.DARK_GRAY));
        searchField.setText(search);
        searchField.setChangedListener(text -> {
            search = text;
            scroll = 0;
            rebuildGrid();
        });
        addDrawableChild(searchField);
        addDrawableChild(ButtonWidget.builder(Text.literal("⚙"), b -> this.client.setScreen(new MenuSettingsScreen(this)))
                .dimensions(rowLeft + searchWidth + 4, y - 1, 20, 20)
                .tooltip(Tooltip.of(Text.literal("Settings: theme, profiles (export/import), key conflicts, reset, what's new.")))
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Ctrl+K"), b -> this.client.setScreen(new CommandPalette(this)))
                .dimensions(rowLeft + searchWidth + 28, y - 1, 50, 20)
                .tooltip(Tooltip.of(Text.literal("Command Palette: run anything by typing its name.")))
                .build());
        gridTop = y + 22;
        gridBottom = this.height - BUTTON_HEIGHT - 8;
        rebuildGrid();
        addBottomRow();
        if (!CheatConfig.get().lastSeenVersion.equals(WhatsNewScreen.VERSION)) {
            CheatConfig.get().lastSeenVersion = WhatsNewScreen.VERSION;
            CheatConfig.markDirty();
            this.client.setScreen(new WhatsNewScreen(this));
        }
    }

    private final List<ButtonWidget> gridButtons = new ArrayList<>();

    /** The scrollable grid. Only the rows that fit between the search box and the bottom row get buttons. */
    private void rebuildGrid() {
        gridButtons.forEach(this::remove);
        gridButtons.clear();
        int columns = Math.max(2, Math.min(5, (this.width - 8) / (BUTTON_WIDTH + GAP)));
        int gridWidth = columns * BUTTON_WIDTH + (columns - 1) * GAP;
        int left = (this.width - gridWidth) / 2;
        List<MenuEntry> list = entries();
        boolean size = search.isBlank() && !favoritesPage && currentCategory == Category.PLAYER;
        int count = list.size() + (size ? 1 : 0);
        int rows = (count + columns - 1) / columns;
        contentHeight = rows * (BUTTON_HEIGHT + GAP);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (gridBottom - gridTop))));
        for (int i = 0; i < count; i++) {
            int x = left + (i % columns) * (BUTTON_WIDTH + GAP);
            int y = gridTop + (i / columns) * (BUTTON_HEIGHT + GAP) - scroll;
            if (y < gridTop || y + BUTTON_HEIGHT > gridBottom) {
                continue;
            }
            ButtonWidget button = i < list.size() ? entryButton(list.get(i), x, y)
                    : ButtonWidget.builder(sizeLabel(), b -> {
                        CheatState.cycleSize();
                        b.setMessage(sizeLabel());
                    }).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.of(Text.literal("Click to cycle: Normal, Giant (4x) and Tiny (1/4).")))
                    .build();
            button.active &= singleplayer();
            gridButtons.add(addDrawableChild(button));
        }
    }

    private ButtonWidget entryButton(MenuEntry entry, int x, int y) {
        boolean allowed = singleplayer() || entry.multiplayer();
        String tooltip = entry.description() + "\n\nRight-click: settings, key, favorite.";
        if (!allowed) {
            tooltip = "Cheats only work in singleplayer.";
        }
        EntryButton button = new EntryButton(x, y, entry, Tooltip.of(Text.literal(tooltip)));
        button.active = allowed;
        return button;
    }

    /** A grid button: left-click uses it, right-click opens its settings. */
    private final class EntryButton extends ButtonWidget {
        private final MenuEntry entry;

        EntryButton(int x, int y, MenuEntry entry, Tooltip tooltip) {
            super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, label(entry), b -> { }, DEFAULT_NARRATION_SUPPLIER);
            this.entry = entry;
            setTooltip(tooltip);
        }

        @Override
        public void onPress() {
            MenuEntries.run(MinecraftClientHolder.get(), MineMenuScreen.this, entry);
            setMessage(label(entry));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1 && this.visible && isMouseOver(mouseX, mouseY)) {
                MinecraftClientHolder.get().setScreen(new FeatureScreen(MineMenuScreen.this, entry));
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    private static Text label(MenuEntry entry) {
        String star = CheatConfig.get().isFavorite(entry.id()) ? "★" : "";
        return switch (entry.kind()) {
            case HACK -> {
                boolean on = entry.isOn() != null && entry.isOn().getAsBoolean();
                MutableText state = Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED);
                yield Text.literal(star + entry.name() + ": ").append(state);
            }
            case SCREEN -> Text.literal(star + entry.name()).formatted(Formatting.AQUA);
            default -> Text.literal(star + entry.name());
        };
    }

    private void addBottomRow() {
        int bottom = this.height - BUTTON_HEIGHT - 4;
        int w = 100;
        int rowLeft = (this.width - (w * 4 + 12)) / 2;
        addDrawableChild(ButtonWidget.builder(onOff("Show HUD", CheatState.hudVisible()), b -> {
                    CheatState.toggleHud();
                    b.setMessage(onOff("Show HUD", CheatState.hudVisible()));
                })
                .dimensions(rowLeft, bottom, w, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.literal("Show the list of active cheats in the top-left corner.")))
                .build());
        ButtonWidget all = addDrawableChild(ButtonWidget.builder(allLabel(), b -> {
                    CheatState.setAll(!CheatState.allOn());
                    clearAndInit();
                })
                .dimensions(rowLeft + (w + 4), bottom, w, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.literal("Turn every ON/OFF hack on at once (on every page). Click again to turn them all off. "
                        + "Noclip, Freecam, Morph, the Nuker, the build tools and the Fun challenges aren't included.")))
                .build());
        all.active = singleplayer();
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(rowLeft + 2 * (w + 4), bottom, w, BUTTON_HEIGHT).build());
        boolean locked = otherFpsCounterOn != null && otherFpsCounterOn.getAsBoolean() && !CheatState.fpsVisible();
        ButtonWidget fps = addDrawableChild(ButtonWidget.builder(onOff("Show FPS", CheatState.fpsVisible()), b -> {
                    CheatState.toggleFps();
                    b.setMessage(onOff("Show FPS", CheatState.fpsVisible()));
                })
                .dimensions(rowLeft + 3 * (w + 4), bottom, w, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.literal(locked ? otherFpsCounterReason : "Show your frames per second in the top-left corner.")))
                .build());
        fps.active = !locked;
    }

    private static Text allLabel() {
        return CheatState.allOn() ? Text.literal("Disable All").formatted(Formatting.RED) : Text.literal("Enable All").formatted(Formatting.GREEN);
    }

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

    private static Text sizeLabel() {
        CheatState.Size size = CheatState.size();
        return Text.literal("Size: ").append(Text.literal(size.displayName())
                .formatted(size == CheatState.Size.NORMAL ? Formatting.GRAY : Formatting.AQUA));
    }

    private static Text onOff(String name, boolean on) {
        return Text.literal(name + ": ").append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= gridTop && mouseY <= gridBottom && contentHeight > gridBottom - gridTop) {
            scroll -= (int) (verticalAmount * (BUTTON_HEIGHT + GAP));
            rebuildGrid();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_K && hasControlDown()) {
            this.client.setScreen(new CommandPalette(this));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        // Labels follow changes made elsewhere (keys, macros, Safe Mode turning something off).
        for (ButtonWidget button : gridButtons) {
            if (button instanceof EntryButton entryButton) {
                entryButton.setMessage(label(entryButton.entry));
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float progress = Theme.openProgress(openedAt);
        context.getMatrices().push();
        context.getMatrices().translate(0, (1 - progress) * 8, 0);
        super.render(context, mouseX, mouseY, delta);
        context.getMatrices().pop();
        if (!hasTabs) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("MineMenu").formatted(Formatting.BOLD),
                    this.width / 2, titleY, Theme.accent());
        }
        if (!singleplayer()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Cheats only work in singleplayer. Info HUD, Minimap and Chunk Borders still work here.")
                    .formatted(Formatting.RED), this.width / 2, gridBottom - 10, 0xFFFFFF);
        }
        if (contentHeight > gridBottom - gridTop) {
            // A thin scroll bar on the right of the grid.
            int barX = this.width - 6;
            int visible = gridBottom - gridTop;
            int barHeight = Math.max(10, visible * visible / contentHeight);
            int barY = gridTop + (visible - barHeight) * scroll / Math.max(1, contentHeight - visible);
            context.fill(barX, gridTop, barX + 3, gridBottom, 0x40FFFFFF);
            context.fill(barX, barY, barX + 3, barY + barHeight, 0xC0000000 | Theme.accent());
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        Theme.overlay(context, this.width, this.height);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Kept for older callers: whether a feature has settings to show. */
    public static boolean hasSettings(Feature feature) {
        return !FeatureSettings.of(feature).isEmpty();
    }
}
