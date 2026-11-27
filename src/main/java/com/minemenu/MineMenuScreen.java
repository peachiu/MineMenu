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
    public static String otherFpsCounterReason = "minemenu.menu.other_fps_counter";

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
        super(Text.translatable("minemenu.text.minemenu"));
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
                .tooltip(Tooltip.of(Text.translatable("minemenu.text.favorites_right_click_any_button_and")))
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
        searchField = new TextFieldWidget(this.textRenderer, rowLeft, y, searchWidth, 18, Text.translatable("minemenu.text.search"));
        searchField.setPlaceholder(Text.translatable("minemenu.text.search_all_hacks_ctrl_k_anywhere").formatted(Formatting.DARK_GRAY));
        searchField.setText(search);
        searchField.setChangedListener(text -> {
            search = text;
            scroll = 0;
            rebuildGrid();
        });
        addDrawableChild(searchField);
        addDrawableChild(ButtonWidget.builder(Text.literal("⚙"), b -> this.client.setScreen(new MenuSettingsScreen(this)))
                .dimensions(rowLeft + searchWidth + 4, y - 1, 20, 20)
                .tooltip(Tooltip.of(Text.translatable("minemenu.text.settings_theme_profiles_export_import_key")))
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.ctrl_k"), b -> this.client.setScreen(new CommandPalette(this)))
                .dimensions(rowLeft + searchWidth + 28, y - 1, 50, 20)
                .tooltip(Tooltip.of(Text.translatable("minemenu.text.command_palette_run_anything_by_typing")))
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
    /** Width of the grid buttons this time (fits the longest label, see rebuildGrid). */
    private int buttonWidth = BUTTON_WIDTH;

    /** The scrollable grid. Only the rows that fit between the search box and the bottom row get buttons. */
    private void rebuildGrid() {
        gridButtons.forEach(this::remove);
        gridButtons.clear();
        List<MenuEntry> list = entries();
        // Buttons as wide as the longest label (longer in some languages), then as many columns as fit.
        int widest = 0;
        for (MenuEntry entry : list) {
            widest = Math.max(widest, this.textRenderer.getWidth(label(entry)));
        }
        buttonWidth = Math.max(BUTTON_WIDTH, Math.min(170, widest + 10));
        int columns = Math.max(2, Math.min(5, (this.width - 8) / (buttonWidth + GAP)));
        buttonWidth = Math.min(buttonWidth, (this.width - 8 - (columns - 1) * GAP) / columns);
        int gridWidth = columns * buttonWidth + (columns - 1) * GAP;
        int left = (this.width - gridWidth) / 2;
        boolean size = search.isBlank() && !favoritesPage && currentCategory == Category.PLAYER;
        int count = list.size() + (size ? 1 : 0);
        int rows = (count + columns - 1) / columns;
        contentHeight = rows * (BUTTON_HEIGHT + GAP);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (gridBottom - gridTop))));
        for (int i = 0; i < count; i++) {
            int x = left + (i % columns) * (buttonWidth + GAP);
            int y = gridTop + (i / columns) * (BUTTON_HEIGHT + GAP) - scroll;
            if (y < gridTop || y + BUTTON_HEIGHT > gridBottom) {
                continue;
            }
            ButtonWidget button = i < list.size() ? entryButton(list.get(i), x, y)
                    : ButtonWidget.builder(sizeLabel(), b -> {
                        CheatState.cycleSize();
                        b.setMessage(sizeLabel());
                    }).dimensions(x, y, buttonWidth, BUTTON_HEIGHT)
                    .tooltip(Tooltip.of(Text.translatable("minemenu.text.click_to_cycle_normal_giant_4x")))
                    .build();
            button.active &= singleplayer();
            gridButtons.add(addDrawableChild(button));
        }
    }

    private ButtonWidget entryButton(MenuEntry entry, int x, int y) {
        boolean byCommand = !singleplayer() && !entry.multiplayer() && com.minemenu.op.OpCommands.active(this.client)
                && com.minemenu.op.OpCommands.supports(entry);
        boolean allowed = singleplayer() || entry.multiplayer() || byCommand;
        String tooltip = entry.description() + "\n\n" + com.minemenu.core.Tr.get("minemenu.menu.right_click_hint", "Right-click: settings, key, favorite.");
        if (byCommand) {
            tooltip = entry.description() + "\n\n" + com.minemenu.core.Tr.get("minemenu.op.tooltip", "On this server it's done with commands (you're OP).");
        } else if (!allowed) {
            tooltip = com.minemenu.core.Tr.get(com.minemenu.op.OpCommands.supports(entry) ? "minemenu.op.needs_op" : "minemenu.menu.singleplayer_only",
                    "Cheats only work in singleplayer.");
        }
        EntryButton button = new EntryButton(x, y, entry, Tooltip.of(Text.literal(tooltip)));
        button.active = allowed;
        return button;
    }

    /** A grid button: left-click uses it, right-click opens its settings. */
    private final class EntryButton extends ButtonWidget {
        private final MenuEntry entry;

        EntryButton(int x, int y, MenuEntry entry, Tooltip tooltip) {
            super(x, y, buttonWidth, BUTTON_HEIGHT, label(entry), b -> { }, DEFAULT_NARRATION_SUPPLIER);
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
                MutableText state = (on ? Text.translatable("minemenu.text.on") : Text.translatable("minemenu.text.off")).formatted(on ? Formatting.GREEN : Formatting.RED);
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
        addDrawableChild(ButtonWidget.builder(onOff("minemenu.menu.show_hud", CheatState.hudVisible()), b -> {
                    CheatState.toggleHud();
                    b.setMessage(onOff("minemenu.menu.show_hud", CheatState.hudVisible()));
                })
                .dimensions(rowLeft, bottom, w, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("minemenu.text.show_the_list_of_active_cheats")))
                .build());
        ButtonWidget all = addDrawableChild(ButtonWidget.builder(allLabel(), b -> {
                    CheatState.setAll(!CheatState.allOn());
                    clearAndInit();
                })
                .dimensions(rowLeft + (w + 4), bottom, w, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("minemenu.text.turn_every_on_off_hack_on")))
                .build());
        all.active = singleplayer();
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.done"), b -> close())
                .dimensions(rowLeft + 2 * (w + 4), bottom, w, BUTTON_HEIGHT).build());
        boolean locked = otherFpsCounterOn != null && otherFpsCounterOn.getAsBoolean() && !CheatState.fpsVisible();
        ButtonWidget fps = addDrawableChild(ButtonWidget.builder(onOff("minemenu.menu.show_fps", CheatState.fpsVisible()), b -> {
                    CheatState.toggleFps();
                    b.setMessage(onOff("minemenu.menu.show_fps", CheatState.fpsVisible()));
                })
                .dimensions(rowLeft + 3 * (w + 4), bottom, w, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(locked ? Text.translatable(otherFpsCounterReason) : Text.translatable("minemenu.text.show_your_frames_per_second_in")))
                .build());
        fps.active = !locked;
    }

    private static Text allLabel() {
        return CheatState.allOn() ? Text.translatable("minemenu.text.disable_all").formatted(Formatting.RED) : Text.translatable("minemenu.text.enable_all").formatted(Formatting.GREEN);
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
        return Text.translatable("minemenu.text.size").append(Text.literal(size.displayName())
                .formatted(size == CheatState.Size.NORMAL ? Formatting.GRAY : Formatting.AQUA));
    }

    private static Text onOff(String name, boolean on) {
        return Text.translatable(name).append(": ").append((on ? Text.translatable("minemenu.text.on") : Text.translatable("minemenu.text.off")).formatted(on ? Formatting.GREEN : Formatting.RED));
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
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("minemenu.text.minemenu").formatted(Formatting.BOLD),
                    this.width / 2, titleY, Theme.accent());
        }
        if (!singleplayer()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("minemenu.text.cheats_only_work_in_singleplayer_info")
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
