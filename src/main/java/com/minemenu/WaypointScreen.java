package com.minemenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/** Up to 5 named waypoints: name them, save where you stand, teleport, show in the HUD, delete. */
public class WaypointScreen extends Screen {
    private static final int ROW_WIDTH = 344;

    private final Screen parent;
    private final TextFieldWidget[] names = new TextFieldWidget[Waypoints.MAX];
    private int seenVersion;

    public WaypointScreen(Screen parent) {
        super(Text.literal("Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        seenVersion = Waypoints.version();
        int left = (this.width - ROW_WIDTH) / 2;
        int y = 36;
        for (int i = 0; i < Waypoints.MAX; i++) {
            int slot = i;
            Waypoints.Waypoint w = Waypoints.get(slot);
            String typed = names[slot] != null ? names[slot].getText() : null; // keep what you typed
            TextFieldWidget name = new TextFieldWidget(this.textRenderer, left, y, 110, 20, Text.literal("Name"));
            name.setMaxLength(24);
            name.setPlaceholder(Text.literal("Waypoint " + (slot + 1)).formatted(Formatting.DARK_GRAY));
            name.setText(typed != null ? typed : w != null ? w.name : "");
            if (w != null) {
                name.setTooltip(Tooltip.of(Text.literal(Waypoints.describe(w))));
            }
            names[slot] = addDrawableChild(name);

            int x = left + 114;
            addDrawableChild(ButtonWidget.builder(Text.literal(w == null ? "Set Here" : "Move Here"),
                            b -> onServer(player -> Waypoints.setHere(player, slot, names[slot].getText())))
                    .dimensions(x, y, 66, 20)
                    .tooltip(Tooltip.of(Text.literal("Save where you're standing (and the way you're facing).")))
                    .build());
            x += 70;
            ButtonWidget go = addDrawableChild(ButtonWidget.builder(Text.literal("Go").formatted(Formatting.GREEN), b -> {
                        onServer(player -> Waypoints.go(player, slot));
                        this.client.setScreen(null);
                    })
                    .dimensions(x, y, 40, 20)
                    .tooltip(Tooltip.of(Text.literal(w == null ? "Nothing saved here yet." : "Teleport to " + Waypoints.describe(w))))
                    .build());
            go.active = w != null;
            x += 44;
            ButtonWidget hud = addDrawableChild(ButtonWidget.builder(
                            Text.literal("HUD: ").append(w != null && w.hud ? Text.literal("ON").formatted(Formatting.GREEN)
                                    : Text.literal("OFF").formatted(Formatting.RED)),
                            b -> onServer(player -> Waypoints.toggleHud(slot)))
                    .dimensions(x, y, 60, 20)
                    .tooltip(Tooltip.of(Text.literal("Show the direction and distance to this waypoint on your screen.")))
                    .build());
            hud.active = w != null;
            x += 64;
            ButtonWidget delete = addDrawableChild(ButtonWidget.builder(Text.literal("Delete").formatted(Formatting.RED),
                            b -> {
                                names[slot].setText("");
                                onServer(player -> Waypoints.delete(slot));
                            })
                    .dimensions(x, y, 50, 20)
                    .build());
            delete.active = w != null;
            y += 24;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions((this.width - 150) / 2, y + 8, 150, 20)
                .build());
    }

    private void onServer(Consumer<ServerPlayerEntity> action) {
        IntegratedServer server = this.client != null ? this.client.getServer() : null;
        if (server != null) {
            server.execute(() -> {
                ServerPlayerEntity player = ServerCheats.host(server);
                if (player != null) {
                    action.accept(player);
                }
            });
        }
    }

    /** Saves typed names (on the server thread, like every other change). */
    private void saveNames() {
        IntegratedServer server = this.client != null ? this.client.getServer() : null;
        if (server == null) {
            return;
        }
        for (int i = 0; i < Waypoints.MAX; i++) {
            int slot = i;
            String text = names[slot].getText();
            server.execute(() -> Waypoints.rename(slot, text));
        }
    }

    @Override
    public void tick() {
        if (this.client != null && !MineMenuClient.isSingleplayer(this.client)) {
            this.client.setScreen(null);
            return;
        }
        if (Waypoints.version() != seenVersion) {
            clearAndInit(); // something changed on the server: show it
        }
    }

    @Override
    public void removed() {
        saveNames();
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Waypoints").formatted(Formatting.AQUA, Formatting.BOLD),
                this.width / 2, 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Saved with this world. Set Home / Go Home use the one called \"Home\".").formatted(Formatting.GRAY),
                this.width / 2, 22, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
