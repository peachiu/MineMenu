package com.minexray.render;

import com.minexray.XrayHost;
import com.minexray.MineXrayClient;
import com.minexray.XrayActions;
import com.minexray.XrayConfig;
import com.minexray.XrayMode;
import com.minexray.scan.VeinMerger;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Vein labels ("Diamond Ore ×6 · 14m"). The world is drawn first and the camera matrices of that frame are kept;
 * the HUD then works out where each vein is on screen and writes the label there as normal screen text. That
 * keeps labels the same size at any distance, and works with mods that change world text rendering
 * (ImmediatelyFast, Sodium, Iris).
 */
public final class LabelRenderer {
    private static final int MAX_LABELS = 24;
    private static final Matrix4f view = new Matrix4f();
    private static final Matrix4f projection = new Matrix4f();
    private static Vec3d camera = Vec3d.ZERO;
    private static boolean captured;
    /** For tests: labels written last frame. */
    public static int drawn;

    private LabelRenderer() {
    }

    /** End of the world pass: remember this frame's camera. */
    public static void capture(WorldRenderContext context) {
        view.set(RenderSystem.getModelViewMatrix()); // already holds the camera rotation at this point
        projection.set(RenderSystem.getProjectionMatrix());
        camera = context.camera().getPos();
        captured = true;
    }

    public static void render(DrawContext context) {
        drawn = 0;
        MinecraftClient client = MinecraftClient.getInstance();
        boolean esp = MineXrayClient.active(XrayMode.ORE_ESP) || MineXrayClient.active(XrayMode.BLOCK_ESP);
        if (!captured || !esp || !XrayConfig.get().labels || client.options.hudHidden) {
            return;
        }
        List<VeinMerger.Vein> veins = VeinMerger.veins();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        Vector4f point = new Vector4f();
        for (int i = 0; i < veins.size() && drawn < MAX_LABELS; i++) {
            VeinMerger.Vein vein = veins.get(i);
            point.set((float) (vein.cx() - camera.x), (float) (vein.cy() + 0.8 - camera.y), (float) (vein.cz() - camera.z), 1f);
            view.transform(point);
            projection.transform(point);
            if (point.w <= 0.05f) {
                continue; // behind you
            }
            float sx = (point.x / point.w * 0.5f + 0.5f) * width;
            float sy = (1f - (point.y / point.w * 0.5f + 0.5f)) * height;
            if (sx < -50 || sx > width + 50 || sy < -10 || sy > height + 10) {
                continue;
            }
            String label = XrayHost.trf("minexray.label", "%s ×%d · %dm", XrayActions.name(vein), vein.count(), Math.round(vein.distance()));
            int w = client.textRenderer.getWidth(label);
            int x = Math.round(sx) - w / 2;
            int y = Math.round(sy) - 4;
            context.fill(x - 2, y - 1, x + w + 2, y + 9, 0x70000000);
            context.drawTextWithShadow(client.textRenderer, label, x, y, 0xFF000000 | vein.color());
            drawn++;
        }
    }
}
