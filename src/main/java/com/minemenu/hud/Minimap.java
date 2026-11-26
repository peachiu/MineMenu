package com.minemenu.hud;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.MineMenuClient;
import com.minemenu.Waypoints;
import com.minemenu.core.FeatureSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.LightType;

/**
 * Minimap: a 128x128 texture built from the per-chunk colour cache (see MapColors), refreshed at most 4 times a
 * second and only when you moved or chunks changed. Waypoints and (in singleplayer) mobs are drawn on top.
 * On servers it still works, but without mob dots or cave mode (no seeing through walls).
 */
public final class Minimap {
    private static final int SIZE = 128;
    private static NativeImageBackedTexture texture;
    private static Identifier textureId;
    private static int lastX = Integer.MIN_VALUE;
    private static int lastZ;
    private static int ticks;
    private static boolean dirty = true;
    private static boolean caveMode;

    /** 3.6: extra dots drawn on the minimap (MineXray's ore veins). */
    public interface Layer {
        /** {@code scale}: minimap pixels per block; (cx, cy) is you; keep dots within {@code size / 2} of it. */
        void draw(DrawContext context, MinecraftClient client, int cx, int cy, double scale, int size);
    }

    public static final java.util.List<Layer> layers = new java.util.concurrent.CopyOnWriteArrayList<>();

    private Minimap() {
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void release() {
        if (texture != null) {
            texture.close();
            texture = null;
            textureId = null;
        }
        MapColors.clear();
        dirty = true;
    }

    /** Client tick: rebuild the picture if needed. */
    public static void tick(MinecraftClient client) {
        if (++ticks % 5 != 0 || client.player == null || client.world == null) {
            return;
        }
        boolean singleplayer = MineMenuClient.isSingleplayer(client);
        boolean underground = singleplayer && client.world.getLightLevel(LightType.SKY, client.player.getBlockPos().up()) == 0
                && client.player.getY() < client.world.getSeaLevel() + 8;
        if (underground != caveMode) {
            caveMode = underground;
            dirty = true;
        }
        if (caveMode) {
            MapColors.setCaveY(client.player.getBlockY());
        }
        int x = client.player.getBlockX();
        int z = client.player.getBlockZ();
        if (!dirty && x == lastX && z == lastZ) {
            return;
        }
        lastX = x;
        lastZ = z;
        dirty = false;
        if (texture == null) {
            texture = new NativeImageBackedTexture(SIZE, SIZE, true);
            textureId = client.getTextureManager().registerDynamicTexture("minemenu_minimap", texture);
        }
        int zoom = (int) FeatureSettings.get(Feature.MINIMAP, "zoom");
        boolean round = FeatureSettings.is(Feature.MINIMAP, "round");
        NativeImage image = texture.getImage();
        int[] budget = {6};
        boolean missing = false;
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                int dx = (px - SIZE / 2) * zoom;
                int dz = (py - SIZE / 2) * zoom;
                if (round && (px - SIZE / 2) * (px - SIZE / 2) + (py - SIZE / 2) * (py - SIZE / 2) > (SIZE / 2) * (SIZE / 2)) {
                    image.setColor(px, py, 0);
                    continue;
                }
                int wx = x + dx;
                int wz = z + dz;
                int[] colors = MapColors.get(client.world, wx >> 4, wz >> 4, caveMode, budget);
                if (colors == null) {
                    image.setColor(px, py, 0xFF202020);
                    missing = true;
                } else {
                    image.setColor(px, py, colors[(wz & 15) * 16 + (wx & 15)]);
                }
            }
        }
        texture.upload();
        if (missing) {
            dirty = true; // fill in the rest over the next refreshes
        }
    }

    public static void render(DrawContext context, MinecraftClient client) {
        if (textureId == null || client.player == null || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
            return;
        }
        int size = (int) FeatureSettings.get(Feature.MINIMAP, "size");
        int right = context.getScaledWindowWidth() - 4;
        int top = client.player.getStatusEffects().isEmpty() ? 18 : 70;
        int left = right - size;
        context.fill(left - 1, top - 1, right + 1, top + size + 1, 0xA0000000);
        context.drawTexture(textureId, left, top, size, size, 0, 0, SIZE, SIZE, SIZE, SIZE);
        int zoom = (int) FeatureSettings.get(Feature.MINIMAP, "zoom");
        double scale = size / (double) SIZE / zoom;
        int cx = left + size / 2;
        int cy = top + size / 2;
        boolean singleplayer = MineMenuClient.isSingleplayer(client);
        if (singleplayer && FeatureSettings.is(Feature.MINIMAP, "mobs") && CheatState.isOn(Feature.MINIMAP)) {
            for (Entity entity : client.world.getEntities()) {
                if (!(entity instanceof LivingEntity) || entity == client.player) {
                    continue;
                }
                double dx = (entity.getX() - client.player.getX()) * scale;
                double dz = (entity.getZ() - client.player.getZ()) * scale;
                if (Math.abs(dx) < size / 2.0 - 1 && Math.abs(dz) < size / 2.0 - 1) {
                    int color = entity instanceof PlayerEntity ? 0xFFFFFFFF : entity instanceof Monster ? 0xFFFF4040 : 0xFF40FF40;
                    context.fill(cx + (int) dx - 1, cy + (int) dz - 1, cx + (int) dx + 1, cy + (int) dz + 1, color);
                }
            }
        }
        for (Layer layer : layers) {
            com.minemenu.core.Guard.run("minimap layer", () -> layer.draw(context, client, cx, cy, scale, size));
        }
        for (int i = 0; i < Waypoints.MAX; i++) {
            Waypoints.Waypoint w = Waypoints.get(i);
            if (w == null || !w.dimension.equals(client.world.getRegistryKey().getValue().toString())) {
                continue;
            }
            double dx = Math.max(-size / 2.0 + 2, Math.min(size / 2.0 - 2, (w.x - client.player.getX()) * scale));
            double dz = Math.max(-size / 2.0 + 2, Math.min(size / 2.0 - 2, (w.z - client.player.getZ()) * scale));
            context.fill(cx + (int) dx - 2, cy + (int) dz - 2, cx + (int) dx + 2, cy + (int) dz + 2, 0xFF55FFFF);
        }
        context.drawCenteredTextWithShadow(client.textRenderer, "N", cx, top + 2, 0xFFFFFF);
        // You: an arrow turned the way you face.
        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(client.player.getYaw() + 180));
        context.drawCenteredTextWithShadow(client.textRenderer, "▲", 0, -4, 0xFFFF55);
        context.getMatrices().pop();
        if (caveMode) {
            context.drawTextWithShadow(client.textRenderer, "cave", left + 2, top + size - 10, 0xAAAAAA);
        }
    }
}
