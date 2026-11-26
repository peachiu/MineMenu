package com.minexray.screen;

import com.minemenu.ui.Theme;
import com.minexray.MineXrayClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.Function;

/**
 * The MineXray tab: sub-pages down the left (Overview, Blocks, Presets, Render, Scanner, HUD, Settings), the
 * chosen page on the right. Same themes as the rest of the menu; fits 683x384 GUI pixels (1366x768 at GUI Scale 2).
 */
public class XrayScreen extends Screen {
    public enum Page { OVERVIEW, BLOCKS, PRESETS, RENDER, SCANNER, HUD, SETTINGS }

    /** Set by MineMeBoost: the MineMenu / MineBoost / MineXray tab row. */
    public static Function<Screen, List<ButtonWidget>> tabs;
    private static Page lastPage = Page.OVERVIEW;
    private static final int SIDE = 86;

    private final Screen parent;
    private Page page;
    private XrayPage content;
    private final boolean startScan;
    private int top;

    public XrayScreen(Screen parent, Page page, boolean startScan) {
        super(Text.translatable("minexray.title"));
        this.parent = parent;
        this.page = page == null ? lastPage : page;
        this.startScan = startScan;
    }

    /** Opens the page you used last. */
    public XrayScreen(Screen parent) {
        this(parent, null, false);
    }

    public net.minecraft.client.MinecraftClient mc() {
        return this.client;
    }

    public net.minecraft.client.font.TextRenderer font() {
        return this.textRenderer;
    }

    public Page page() {
        return page;
    }

    public XrayPage content() {
        return content;
    }

    public void show(Page next) {
        page = next;
        lastPage = next;
        clearAndInit();
    }

    @Override
    protected void init() {
        lastPage = page;
        int y = 2;
        List<ButtonWidget> tabRow = tabs != null ? tabs.apply(this) : List.of();
        if (!tabRow.isEmpty()) {
            int total = -4;
            for (ButtonWidget b : tabRow) {
                total += b.getWidth() + 4;
            }
            int x = (this.width - total) / 2;
            for (ButtonWidget b : tabRow) {
                b.setPosition(x, y);
                x += b.getWidth() + 4;
                addDrawableChild(b);
            }
            y += 24;
        }
        top = y + 12;
        int py = top;
        for (Page p : Page.values()) {
            ButtonWidget button = ButtonWidget.builder(Text.translatable("minexray.page." + p.name().toLowerCase(java.util.Locale.ROOT)), b -> show(p))
                    .dimensions(6, py, SIDE - 6, 20).build();
            button.active = p != page;
            addDrawableChild(button);
            py += 22;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close()).dimensions(6, this.height - 26, SIDE - 6, 20).build());
        content = switch (page) {
            case OVERVIEW -> new OverviewPage();
            case BLOCKS -> new BlocksPage();
            case PRESETS -> new PresetsPage();
            case RENDER -> new RenderPage();
            case SCANNER -> new ScannerPage(startScan);
            case HUD -> new HudPage();
            case SETTINGS -> new SettingsPage();
        };
        if (MineXrayClient.allowed() || page == Page.SETTINGS) {
            content.init(this, SIDE + 8, top, this.width - SIDE - 14, this.height - top - 6);
        }
    }

    /** For pages: adds a widget to the screen. */
    public <T extends Element & Drawable & Selectable> T add(T widget) {
        return addDrawableChild(widget);
    }

    public void rebuild() {
        clearAndInit();
    }

    @Override
    public void tick() {
        if (content != null) {
            content.tick(this);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(this.textRenderer, this.title.copy().formatted(Formatting.BOLD), SIDE + 8, top - 11, Theme.accent());
        if (!MineXrayClient.allowed() && page != Page.SETTINGS) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("minexray.singleplayer_only").formatted(Formatting.RED),
                    SIDE + (this.width - SIDE) / 2, this.height / 2, 0xFFFFFF);
            return;
        }
        if (content != null) {
            content.render(this, context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        Theme.overlay(context, this.width, this.height);
        Theme.panel(context, 3, top - 3, SIDE + 3, top + Page.values().length * 22 + 1);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (content != null && mouseX > SIDE && content.scrolled(this, vertical)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
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
