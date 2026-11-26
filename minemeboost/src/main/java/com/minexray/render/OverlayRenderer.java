package com.minexray.render;

import com.minexray.MineXrayClient;
import com.minexray.XrayActions;
import com.minexray.XrayConfig;
import com.minexray.XrayMode;
import com.minexray.mode.LavaWarning;
import com.minexray.scan.VeinMerger;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * What's drawn fresh every frame (it moves or is short-lived): tracers to the nearest veins, vein labels,
 * Entity ESP boxes, the 30-second "Show" highlights from the scanner and the Lava Warning box.
 */
public final class OverlayRenderer {
    private static final int MAX_ENTITIES = 300;
    private static final int MAX_LABELS = 24;
    private record Highlight(BlockPos pos, int color, long until) {
    }

    private static final List<Highlight> highlights = new ArrayList<>();
    /** Labels get their own buffer: mods like ImmediatelyFast take over the game's shared text batching. */
    private static net.minecraft.client.util.BufferAllocator labelBuffer;
    /** For tests. */
    public static int drawnEntities;

    private OverlayRenderer() {
    }

    public static void highlight(BlockPos pos, int color, long millis) {
        highlights.removeIf(h -> h.pos().equals(pos));
        highlights.add(new Highlight(pos, color, System.currentTimeMillis() + millis));
    }

    public static int highlights() {
        return highlights.size();
    }

    public static void reset() {
        highlights.clear();
        drawnEntities = 0;
    }

    public static void render(WorldRenderContext context) {
        XrayConfig config = XrayConfig.get();
        boolean esp = MineXrayClient.active(XrayMode.ORE_ESP) || MineXrayClient.active(XrayMode.BLOCK_ESP);
        boolean entities = MineXrayClient.active(XrayMode.ENTITY_ESP);
        boolean lava = MineXrayClient.active(XrayMode.LAVA_WARNING) && LavaWarning.showing() && LavaWarning.lavaAt() != null;
        long now = System.currentTimeMillis();
        highlights.removeIf(h -> h.until() < now);
        if (!esp && !entities && !lava && highlights.isEmpty()) {
            drawnEntities = 0;
            return;
        }
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        Matrix4f matrix = new Matrix4f(); // the model-view matrix already holds the camera rotation at this point
        List<VeinMerger.Vein> veins = esp ? VeinMerger.veins() : List.of();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        if (esp && config.tracers) {
            Vec3d start = cam.add(Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(0.6));
            int count = Math.min(veins.size(), MineXrayClient.tracerCount());
            for (int i = 0; i < count; i++) {
                VeinMerger.Vein vein = veins.get(i);
                line(buffer, matrix, cam, start.x, start.y, start.z, vein.cx(), vein.cy(), vein.cz(), vein.color(), 200);
            }
        }
        if (entities) {
            drawEntities(buffer, matrix, cam, config, context.tickCounter().getTickDelta(true));
        } else {
            drawnEntities = 0;
        }
        for (Highlight h : highlights) {
            float pulse = 0.6f + 0.4f * (float) Math.sin(now / 150.0);
            int alpha = (int) (255 * pulse);
            box(buffer, matrix, cam, new Box(h.pos()).expand(0.05), h.color(), alpha);
            Vec3d start = cam.add(Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(0.6));
            line(buffer, matrix, cam, start.x, start.y, start.z, h.pos().getX() + 0.5, h.pos().getY() + 0.5, h.pos().getZ() + 0.5, h.color(), alpha);
        }
        if (lava) {
            box(buffer, matrix, cam, new Box(LavaWarning.lavaAt()).expand(0.02), 0xFF3000, 255);
        }
        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.lineWidth(config.lineWidth);
            BufferRenderer.drawWithGlobalProgram(built);
            RenderSystem.lineWidth(1.0f);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
        }
        if (esp && config.labels && !veins.isEmpty()) {
            drawLabels(context, veins);
        }
    }

    private static void drawEntities(BufferBuilder buffer, Matrix4f matrix, Vec3d cam, XrayConfig config, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        double r2 = Math.pow(MineXrayClient.radius(config.entityRadius), 2);
        boolean filled = "BOX".equals(config.entityStyle);
        int drawn = 0;
        for (Entity entity : client.world.getEntities()) {
            if (drawn >= MAX_ENTITIES) {
                break;
            }
            if (entity == client.player || entity == client.getCameraEntity() || entity.squaredDistanceTo(cam) > r2) {
                continue;
            }
            int color = color(entity, config);
            if (color < 0) {
                continue;
            }
            Vec3d lerped = entity.getLerpedPos(tickDelta);
            Box box = entity.getBoundingBox().offset(lerped.subtract(entity.getPos()));
            box(buffer, matrix, cam, box, color, 230);
            if (filled) { // a cross on each side reads as a solid box without a second pass
                line(buffer, matrix, cam, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, 120);
                line(buffer, matrix, cam, box.maxX, box.minY, box.minZ, box.minX, box.maxY, box.maxZ, color, 120);
            }
            drawn++;
        }
        drawnEntities = drawn;
    }

    /** Hostile red, passive green, players blue, items yellow; -1 = not shown. */
    static int color(Entity entity, XrayConfig config) {
        if (!config.entityOnly.isEmpty() && !config.entityOnly.contains(Registries.ENTITY_TYPE.getId(entity.getType()).toString())) {
            return -1;
        }
        if (entity instanceof PlayerEntity) {
            return config.entityPlayers ? 0x4080FF : -1;
        }
        if (entity instanceof ItemEntity) {
            return config.entityItems ? 0xFFE040 : -1;
        }
        if (entity instanceof Monster) {
            return config.entityHostile ? 0xFF3030 : -1;
        }
        if (entity instanceof LivingEntity) {
            return config.entityPassive ? 0x40FF40 : -1;
        }
        return -1;
    }

    private static void drawLabels(WorldRenderContext context, List<VeinMerger.Vein> veins) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer text = client.textRenderer;
        if (labelBuffer == null) {
            labelBuffer = new net.minecraft.client.util.BufferAllocator(1 << 16);
        }
        VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(labelBuffer);
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        int shown = 0;
        for (VeinMerger.Vein vein : veins) {
            if (shown++ >= MAX_LABELS) {
                break;
            }
            String label = com.minemenu.core.Tr.format("minexray.label", "%s ×%d · %dm", XrayActions.name(vein), vein.count(), Math.round(vein.distance()));
            MatrixStack matrices = new MatrixStack();
            matrices.translate(vein.cx() - cam.x, vein.cy() + 0.9 - cam.y, vein.cz() - cam.z);
            matrices.multiply(camera.getRotation());
            float scale = 0.025f * (float) Math.max(1.0, vein.distance() / 10.0);
            matrices.scale(scale, -scale, scale);
            float x = -text.getWidth(label) / 2f;
            text.draw(label, x, 0, 0xFF000000 | vein.color(), false, matrices.peek().getPositionMatrix(), consumers,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0x60000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
        consumers.draw();
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix, Vec3d cam, double x1, double y1, double z1, double x2, double y2, double z2,
                             int color, int alpha) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        buffer.vertex(matrix, (float) (x1 - cam.x), (float) (y1 - cam.y), (float) (z1 - cam.z)).color(r, g, b, alpha);
        buffer.vertex(matrix, (float) (x2 - cam.x), (float) (y2 - cam.y), (float) (z2 - cam.z)).color(r, g, b, alpha);
    }

    private static void box(BufferBuilder buffer, Matrix4f matrix, Vec3d cam, Box b, int color, int alpha) {
        double[][] edges = {
                {b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ}, {b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ},
                {b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ}, {b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ},
                {b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ}, {b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ},
                {b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ}, {b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ},
                {b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ}, {b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ},
                {b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ}, {b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ}};
        for (double[] e : edges) {
            line(buffer, matrix, cam, e[0], e[1], e[2], e[3], e[4], e[5], color, alpha);
        }
    }
}
