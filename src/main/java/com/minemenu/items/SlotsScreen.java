package com.minemenu.items;

import com.minemenu.ui.MenuEntries;
import com.minemenu.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Five slots with Save / Load: used for Armor Loadouts and Inventory Snapshots. */
public class SlotsScreen extends Screen {
    private final Screen parent;
    private final boolean loadouts;

    public SlotsScreen(Screen parent, boolean loadouts) {
        super(Text.literal(loadouts ? "Armor Loadouts" : "Inventory Snapshots"));
        this.parent = parent;
        this.loadouts = loadouts;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 150;
        int y = 34;
        for (int i = 0; i < Inventories.SLOTS; i++) {
            int slot = i;
            addDrawableChild(ButtonWidget.builder(Text.literal((loadouts ? "Save loadout " : "Save snapshot ") + (i + 1)), b ->
                    MenuEntries.onServer(p -> {
                        if (loadouts) Inventories.saveLoadout(p, slot); else Inventories.saveSnapshot(p, slot);
                    }).accept(MinecraftClient.getInstance(), null)).dimensions(left, y, 148, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal((loadouts ? "Equip " : "Restore ") + (i + 1)).formatted(Formatting.GREEN), b -> {
                MenuEntries.onServer(p -> {
                    if (loadouts) Inventories.equipLoadout(p, slot); else Inventories.restoreSnapshot(p, slot);
                }).accept(MinecraftClient.getInstance(), null);
                close();
            }).dimensions(left + 152, y, 148, 20).build());
            y += 24;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(this.width / 2 - 75, y + 6, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title.copy().formatted(Formatting.BOLD), this.width / 2, 10, Theme.accent());
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(loadouts ? "Armor and the items in your hands. Saved with this world."
                : "Your whole inventory. Restoring replaces what you carry now. Saved with this world.").formatted(Formatting.GRAY), this.width / 2, 21, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
