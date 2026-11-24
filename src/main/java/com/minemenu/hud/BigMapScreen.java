package com.minemenu.hud;

import com.minemenu.MineMenuClient;
import com.minemenu.Waypoints;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** The big map (M): the chunks the minimap has seen, around you; scroll to zoom, drag to look around. */
public class BigMapScreen extends Screen {
    private int zoom = 2;
    private double offsetX;
    private double offsetZ;

    public BigMapScreen() {
        super(Text.literal("Map"));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        zoom = Math.max(1, Math.min(8, zoom + (verticalAmount > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        offsetX -= deltaX * zoom;
        offsetZ -= deltaY * zoom;
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (this.client.player == null || this.client.world == null) {
            return;
        }
        int left = 10;
        int top = 20;
        int width = this.width - 20;
        int height = this.height - 30;
        context.fill(left, top, left + width, top + height, 0xFF101010);
        double centerX = this.client.player.getX() + offsetX;
        double centerZ = this.client.player.getZ() + offsetZ;
        boolean cave = false;
        int[] budget = {12};
        // One rectangle per (zoom x zoom) group of blocks: cheap enough for a menu, nothing runs while it's closed.
        int step = 2;
        for (int py = 0; py < height; py += step) {
            for (int px = 0; px < width; px += step) {
                int wx = (int) Math.floor(centerX + (px - width / 2.0) * zoom);
                int wz = (int) Math.floor(centerZ + (py - height / 2.0) * zoom);
                int[] colors = MapColors.get(this.client.world, wx >> 4, wz >> 4, cave, budget);
                if (colors != null) {
                    int abgr = colors[(wz & 15) * 16 + (wx & 15)];
                    int argb = (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
                    context.fill(left + px, top + py, left + px + step, top + py + step, argb);
                }
            }
        }
        for (int i = 0; i < Waypoints.MAX; i++) {
            Waypoints.Waypoint w = Waypoints.get(i);
            if (w == null || !w.dimension.equals(this.client.world.getRegistryKey().getValue().toString())) {
                continue;
            }
            int x = left + width / 2 + (int) ((w.x - centerX) / zoom);
            int y = top + height / 2 + (int) ((w.z - centerZ) / zoom);
            if (x > left && x < left + width && y > top && y < top + height) {
                context.fill(x - 2, y - 2, x + 2, y + 2, 0xFF55FFFF);
                context.drawTextWithShadow(this.textRenderer, w.name, x + 4, y - 4, 0x55FFFF);
            }
        }
        int px = left + width / 2 - (int) (offsetX / zoom);
        int py = top + height / 2 - (int) (offsetZ / zoom);
        context.fill(px - 2, py - 2, px + 2, py + 2, 0xFFFFFF55);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Map (" + zoom + " blocks per pixel). Scroll: zoom, drag: move. "
                + (MineMenuClient.isSingleplayer(this.client) ? "" : "Only explored chunks.")).formatted(Formatting.GRAY), this.width / 2, 6, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
