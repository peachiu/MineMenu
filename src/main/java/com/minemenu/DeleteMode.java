package com.minemenu;

import com.minemenu.mixin.HandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Delete Items: opens your normal inventory, where right-clicking an item deletes it.
 * Closing the inventory turns it off again. An Undo button gives deleted items back.
 */
public final class DeleteMode {
    private static boolean active;
    /** How many deletes can be undone (mirrors the server-side list, for the button). */
    private static int undoable;
    private static ButtonWidget undoButton;

    private DeleteMode() {
    }

    public static boolean isActive() {
        return active;
    }

    /** Opens the inventory in delete mode (from the MineMenu Items page). */
    public static void open(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        active = true;
        // In creative, vanilla swaps this for the creative inventory; delete mode works there too.
        client.setScreen(new InventoryScreen(client.player));
    }

    private static boolean isInventory(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen;
    }

    /** True if right-clicks on this screen should delete (delete mode is on and it's your inventory). */
    public static boolean appliesTo(Screen screen) {
        return active && isInventory(screen);
    }

    /** A right-click in delete mode: delete the item in that slot if it's one of yours. */
    public static void onRightClick(Slot slot) {
        if (slot != null && slot.hasStack() && slot.inventory instanceof PlayerInventory) {
            delete(MinecraftClient.getInstance(), slot.getIndex());
            if (undoButton != null) {
                undoButton.active = true;
            }
        }
    }

    /** Hooks into every inventory screen as it opens. Called once at startup. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!active || !isInventory(screen)) {
                return;
            }
            ButtonWidget undo = ButtonWidget.builder(Text.translatable("minemenu.text.undo_delete"), b -> undo(client))
                    .dimensions(scaledWidth / 2 - 50, scaledHeight - 24, 100, 20)
                    .tooltip(Tooltip.of(Text.translatable("minemenu.text.give_back_the_last_item_you")))
                    .build();
            undo.active = undoable > 0;
            Screens.getButtons(screen).add(undo);
            undoButton = undo;

            ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> drawOverlay(s, context));
        });
        // Leaving the inventory (closing it, or opening anything else) ends delete mode.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (active && !isInventory(client.currentScreen)) {
                active = false;
                undoButton = null;
            }
        });
    }

    private static void drawOverlay(Screen screen, DrawContext context) {
        if (!active || !(screen instanceof HandledScreen<?>)) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        context.drawCenteredTextWithShadow(client.textRenderer,
                Text.translatable("minemenu.text.delete_mode_right_click_an_item").formatted(Formatting.RED, Formatting.BOLD),
                screen.width / 2, 6, 0xFFFFFF);
        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
        Slot slot = accessor.minemenu$getFocusedSlot();
        if (slot != null && slot.hasStack() && slot.inventory instanceof PlayerInventory) {
            int x = accessor.minemenu$getX() + slot.x;
            int y = accessor.minemenu$getY() + slot.y;
            context.fill(x, y, x + 16, y + 16, 0x80FF0000); // red: this one goes if you right-click
        }
    }

    private static void delete(MinecraftClient client, int inventoryIndex) {
        IntegratedServer server = client.getServer();
        if (server == null || !MineMenuClient.isSingleplayer(client)) {
            return;
        }
        undoable++;
        server.execute(() -> {
            var player = ServerCheats.host(server);
            if (player != null) {
                ItemCheats.deleteInventorySlot(player, inventoryIndex);
            }
        });
    }

    private static void undo(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null || undoable <= 0) {
            return;
        }
        undoable--;
        server.execute(() -> {
            var player = ServerCheats.host(server);
            if (player != null) {
                ItemCheats.undoDelete(player);
            }
        });
        if (undoButton != null) {
            undoButton.active = undoable > 0;
        }
    }

    /** A new world: nothing to undo. */
    public static void reset() {
        undoable = 0;
        active = false;
        undoButton = null;
    }
}
