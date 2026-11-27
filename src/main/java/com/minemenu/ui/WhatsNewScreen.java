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
    /** The version this screen describes (MineMeBoostXray sets its own). */
    public static String VERSION = "6.0.0";
    /** Lang keys, shown in order ("" = empty line). */
    public static List<String> LINES = List.of(
            "minemenu.whats_new.title", "",
            "minemenu.whats_new.admin", "",
            "minemenu.whats_new.servers", "",
            "minemenu.whats_new.fps", "",
            "minemenu.whats_new.build", "",
            "minemenu.whats_new.language", "",
            "minemenu.whats_new.tests");

    private final Screen parent;

    public WhatsNewScreen(Screen parent) {
        super(Text.translatable("minemenu.text.what_s_new"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.let_s_go"), b -> close())
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
                wrapped.addAll(this.textRenderer.wrapLines(Text.translatable(line), maxWidth));
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
