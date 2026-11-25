package com.minemenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Structure Finder: pick a structure, the nearest one shows up in your HUD with direction and distance. */
public class StructureScreen extends Screen {
    private static final int BUTTON_WIDTH = 120;

    private final Screen parent;

    public StructureScreen(Screen parent) {
        super(Text.translatable("minemenu.text.find_structure"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        WorldCheats.Target[] targets = WorldCheats.Target.values();
        int columns = this.width >= 3 * BUTTON_WIDTH + 16 ? 3 : 2;
        int left = (this.width - (columns * BUTTON_WIDTH + (columns - 1) * 4)) / 2;
        int y = 32;
        for (int i = 0; i < targets.length; i++) {
            WorldCheats.Target target = targets[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(target.displayName()), b -> find(target))
                    .dimensions(left + (i % columns) * (BUTTON_WIDTH + 4), y + (i / columns) * 22, BUTTON_WIDTH, 20)
                    .build());
        }
        int bottom = y + ((targets.length + columns - 1) / columns) * 22 + 6;
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.clear_target"), b -> {
                    Waypoints.setFound(null);
                    close();
                })
                .dimensions(this.width / 2 - 104, bottom, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("minemenu.text.back"), b -> close())
                .dimensions(this.width / 2 + 4, bottom, 100, 20).build());
    }

    private void find(WorldCheats.Target target) {
        IntegratedServer server = this.client != null ? this.client.getServer() : null;
        if (server == null) {
            return;
        }
        if (this.client.player != null) {
            this.client.player.sendMessage(Text.translatable("minemenu.text.searching_for_the_nearest_s", target.displayName())
                    .formatted(Formatting.GRAY), true);
        }
        server.execute(() -> {
            ServerPlayerEntity player = ServerCheats.host(server);
            if (player != null) {
                WorldCheats.findStructure(player, target);
            }
        });
        this.client.setScreen(null);
    }

    @Override
    public void tick() {
        if (this.client != null && !MineMenuClient.isSingleplayer(this.client)) {
            this.client.setScreen(null);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("minemenu.text.find_structure").formatted(Formatting.GREEN, Formatting.BOLD),
                this.width / 2, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("minemenu.text.searches_your_dimension_like_locate_the").formatted(Formatting.GRAY),
                this.width / 2, 19, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
