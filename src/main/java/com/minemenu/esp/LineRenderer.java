package com.minemenu.esp;

import com.minemenu.core.Guard;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws coloured line boxes in the world, visible through walls: Ore Vision, Chest ESP, slime chunks, light
 * levels, the build selection and paste preview. Since 3.4 this replaces glowing entities, which made the game
 * run its full-screen outline effect (expensive on integrated graphics) and sent hundreds of entities around.
 * One batch per frame, nothing at all when no provider has anything to show.
 */
public final class LineRenderer {
    /** Something that adds boxes every frame. */
    public interface Provider {
        void draw(Sink sink, Vec3d camera);
    }

    /** Where providers put their boxes and lines. Colours are 0xRRGGBB. */
    public static final class Sink {
        private BufferBuilder buffer;
        private Matrix4f matrix;
        private Vec3d camera;
        private int count;

        public void box(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
            line(x1, y1, z1, x2, y1, z1, color);
            line(x2, y1, z1, x2, y1, z2, color);
            line(x2, y1, z2, x1, y1, z2, color);
            line(x1, y1, z2, x1, y1, z1, color);
            line(x1, y2, z1, x2, y2, z1, color);
            line(x2, y2, z1, x2, y2, z2, color);
            line(x2, y2, z2, x1, y2, z2, color);
            line(x1, y2, z2, x1, y2, z1, color);
            line(x1, y1, z1, x1, y2, z1, color);
            line(x2, y1, z1, x2, y2, z1, color);
            line(x2, y1, z2, x2, y2, z2, color);
            line(x1, y1, z2, x1, y2, z2, color);
        }

        /** A block-sized box, a hair bigger than the block. */
        public void block(int x, int y, int z, int color) {
            box(x - 0.002, y - 0.002, z - 0.002, x + 1.002, y + 1.002, z + 1.002, color);
        }

        public void line(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            buffer.vertex(matrix, (float) (x1 - camera.x), (float) (y1 - camera.y), (float) (z1 - camera.z)).color(r, g, b, 230);
            buffer.vertex(matrix, (float) (x2 - camera.x), (float) (y2 - camera.y), (float) (z2 - camera.z)).color(r, g, b, 230);
            count++;
        }
    }

    private static final List<Provider> providers = new ArrayList<>();
    private static final Sink sink = new Sink();

    private LineRenderer() {
    }

    public static void register(Provider provider) {
        providers.add(provider);
    }

    public static void init() {
        WorldRenderEvents.LAST.register(context -> Guard.run("line renderer", () -> render(context)));
    }

    private static void render(WorldRenderContext context) {
        if (providers.isEmpty()) {
            return;
        }
        sink.buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        // 3.6 fix: at WorldRenderEvents.LAST the model-view matrix already holds the camera rotation, so the
        // vertices must not be rotated again (boxes drifted away from their blocks when looking up or down).
        sink.matrix = new Matrix4f();
        sink.camera = context.camera().getPos();
        sink.count = 0;
        for (Provider provider : providers) {
            provider.draw(sink, sink.camera);
        }
        BuiltBuffer built = sink.buffer.endNullable();
        sink.buffer = null;
        if (built == null) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(2.0f);
        BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}
