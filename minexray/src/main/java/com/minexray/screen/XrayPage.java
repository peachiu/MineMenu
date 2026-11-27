package com.minexray.screen;

import net.minecraft.client.gui.DrawContext;

/** One sub-page of the MineXray tab, laid out in the area right of the page list. */
public interface XrayPage {
    void init(XrayScreen screen, int x, int y, int width, int height);

    default void render(XrayScreen screen, DrawContext context, int mouseX, int mouseY, float delta) {
    }

    default void tick(XrayScreen screen) {
    }

    /** Mouse wheel over the page; true if used. */
    default boolean scrolled(XrayScreen screen, double amount) {
        return false;
    }
}
