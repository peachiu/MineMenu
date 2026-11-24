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

/** Up to 10 named waypoints (5 per page) and Last Death: name them, save where you stand, teleport, show in the HUD, delete. */
public class WaypointScreen extends Screen {
    private static final int ROW_WIDTH = 344;

    private final Screen parent;
    private final TextFieldWidget[] names = new TextFieldWidget[Waypoints.MAX];
    private int seenVersion;
    /** 5 waypoints per page, so it fits small windows. */
    private static int page;
    private static final int PER_PAGE = 5;

    public WaypointScreen(Screen parent) {
        super(Text.literal("Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        seenVersion = Waypoints.version();
        int left = (this.width - ROW_WIDTH) / 2;
        int y = 36;
        for (int i = page * PER_PAGE; i < Math.min(Waypoints.MAX, (page + 1) * PER_PAGE); i++) {
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
        // Last Death (set automatically when you die)
        Waypoints.Waypoint death = Waypoints.lastDeath();
        ButtonWidget goDeath = addDrawableChild(ButtonWidget.builder(Text.literal("Go to Last Death").formatted(Formatting.RED), b -> {
                    onServer(Waypoints::goLastDeath);
                    this.client.setScreen(null);
                })
                .dimensions(left, y + 2, 180, 20)
                .tooltip(Tooltip.of(Text.literal(death == null ? "You haven't died in this world yet." : "Teleport to " + Waypoints.describe(death))))
                .build());
        goDeath.active = death != null;
        ButtonWidget deleteDeath = addDrawableChild(ButtonWidget.builder(Text.literal("Forget it"), b -> onServer(p -> Waypoints.deleteLastDeath()))
                .dimensions(left + 184, y + 2, 70, 20).build());
        deleteDeath.active = death != null;
        y += 28;
        int pages = (Waypoints.MAX + PER_PAGE - 1) / PER_PAGE;
        ButtonWidget prev = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            saveNames();
            page = Math.max(0, page - 1);
            clearAndInit();
        }).dimensions(left, y, 30, 20).build());
        prev.active = page > 0;
        addDrawableChild(ButtonWidget.builder(Text.literal("Done (page " + (page + 1) + "/" + pages + ")"), b -> close())
                .dimensions(left + 34, y, ROW_WIDTH - 68, 20)
                .build());
        ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            saveNames();
            page = Math.min(pages - 1, page + 1);
            clearAndInit();
        }).dimensions(left + ROW_WIDTH - 30, y, 30, 20).build());
        next.active = page < pages - 1;
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
            if (names[slot] == null) {
                continue;
            }
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
