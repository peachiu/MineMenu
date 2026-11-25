package com.minemenu.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.text.OrderedText;

/** Shown once after updating: the highlights of this version. */
public class WhatsNewScreen extends Screen {
    public static final String VERSION = "3.4.0";
    private static final List<String> LINES = List.of(
            "§dMineMeBoost 3.4.0",
            "",
            "§aFPS: §fRender Scale (draw the world at a lower resolution, sharp HUD), Dynamic Render Scale, Lag Doctor (finds what costs you FPS), spike log, frame-time graph, Battery Saver, Memory panel, JVM Advisor, context rules (Nether, caves, elytra...) and an A/B test of every boost.",
            "",
            "§bMenu: §fsearch, favorites (★), a key for any hack, profiles you can share, Command Palette (Ctrl+K), themes, and Safe Mode: a hack that errors is switched off instead of crashing the game.",
            "",
            "§eNew pages: §fBuild (copy/paste, schematics, structures, brushes, biome painter, gravity gun), Fun (Chaos Mode, Lucky Blocks, challenges, meteors, tornado) and Utility (minimap, info HUD, light levels, chunk borders, slime chunks, health bars, macros).",
            "",
            "§6And: §fFreecam, Full Bright, Double Jump, Dash, Morph, Phase, Item/Potion/Mob Creator, Death Chest, Boss Arena, Mind Control, Bow Aimbot... Right-click any button to set it up.",
            "",
            "§7Cheats now also work in your own LAN world (only for you, the host).");

    private final Screen parent;

    public WhatsNewScreen(Screen parent) {
        super(Text.literal("What's new"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Let's go!"), b -> close())
                .dimensions((this.width - 150) / 2, this.height - 28, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // Wrapped to the window, so it also fits small windows and big GUI scales.
        int maxWidth = Math.min(440, this.width - 20);
        List<OrderedText> wrapped = new ArrayList<>();
        for (String line : LINES) {
            if (line.isEmpty()) {
                wrapped.add(OrderedText.EMPTY);
            } else {
                wrapped.addAll(this.textRenderer.wrapLines(Text.literal(line), maxWidth));
            }
        }
        int y = Math.max(6, (this.height - 36 - wrapped.size() * 10) / 2);
        for (OrderedText line : wrapped) {
            context.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, y, 0xFFFFFF);
            y += 10;
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
