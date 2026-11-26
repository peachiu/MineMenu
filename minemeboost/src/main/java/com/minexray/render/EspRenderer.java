package com.minexray.render;

import com.minexray.MineXrayClient;
import com.minexray.XrayConfig;
import com.minexray.XrayMode;
import com.minexray.scan.MeshData;
import com.minexray.scan.ScanCache;
import com.minexray.scan.SectionData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws the scanned geometry. Sections are grouped into regions of 4x4x4 sections (64 blocks a side), and each
 * region's lines and faces live in GPU buffers that are only rebuilt when something in the region changes. So a
 * frame is a handful of draw calls, however many blocks are highlighted. Faded with distance by a small shader.
 * The block limit keeps only the nearest sections' blocks.
 */
public final class EspRenderer {
    private static final int REGION_SHIFT = 2;
    private static final boolean[] LINES = {true, false, true, false, true};
    private static ShaderProgram program;
    private static final Map<Long, Region> regions = new HashMap<>();
    private static BufferAllocator allocator;
    private static int lastChanges = -1;
    private static long lastCameraSection = Long.MIN_VALUE;
    private static int lastLayersKey;
    /** For tests and the Overview page. */
    public static int drawCalls;
    public static int drawnEspBlocks;

    private EspRenderer() {
    }

    private static final class Region {
        final int originX;
        final int originY;
        final int originZ;
        final VertexBuffer[] buffers = new VertexBuffer[MeshData.LAYERS];
        final long[] built = new long[MeshData.LAYERS];

        Region(long key) {
            originX = ChunkSectionPos.unpackX(key) << (4 + REGION_SHIFT);
            originY = ChunkSectionPos.unpackY(key) << (4 + REGION_SHIFT);
            originZ = ChunkSectionPos.unpackZ(key) << (4 + REGION_SHIFT);
        }

        void close() {
            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i] != null) {
                    buffers[i].close();
                    buffers[i] = null;
                }
                built[i] = 0;
            }
        }
    }

    public static void registerShader() {
        CoreShaderRegistrationCallback.EVENT.register(context ->
                context.register(Identifier.of("minexray", "esp"), VertexFormats.POSITION_COLOR, loaded -> program = loaded));
    }

    /** Frees every GPU buffer (left the world, changed dimension, or switched everything off). Render thread. */
    public static void reset() {
        for (Region region : regions.values()) {
            region.close();
        }
        regions.clear();
        lastChanges = -1;
        drawCalls = 0;
        drawnEspBlocks = 0;
    }

    public static void render(WorldRenderContext context) {
        XrayConfig config = XrayConfig.get();
        boolean esp = MineXrayClient.active(XrayMode.ORE_ESP) || MineXrayClient.active(XrayMode.BLOCK_ESP);
        boolean fluid = MineXrayClient.active(XrayMode.FLUID_VIEW);
        boolean cave = MineXrayClient.active(XrayMode.CAVE_FINDER);
        if (!esp && !fluid && !cave) {
            if (!regions.isEmpty()) {
                reset();
            }
            return;
        }
        Vec3d camera = context.camera().getPos();
        int espRadius = MineXrayClient.radius(MineXrayClient.espRadius(config));
        int fluidRadius = MineXrayClient.radius(config.fluidRadius);
        int caveRadius = MineXrayClient.radius(config.caveRadius);
        long cameraSection = ChunkSectionPos.asLong((int) Math.floor(camera.x) >> 4, (int) Math.floor(camera.y) >> 4, (int) Math.floor(camera.z) >> 4);
        int layersKey = java.util.Objects.hash(esp, fluid, cave, espRadius, fluidRadius, caveRadius, config.blockLimit, config.caveLargeOnly, config.caveMinVolume);
        if (ScanCache.changes() != lastChanges || cameraSection != lastCameraSection || layersKey != lastLayersKey) {
            lastChanges = ScanCache.changes();
            lastCameraSection = cameraSection;
            lastLayersKey = layersKey;
            select(camera, esp ? espRadius : 0, fluid ? fluidRadius : 0, cave ? caveRadius : 0, config);
        }
        draw(context, camera, config, esp, fluid, cave, espRadius, fluidRadius, caveRadius);
    }

    /** Chooses which sections each region shows, and rebuilds the regions whose choice changed. */
    private static void select(Vec3d camera, int espRadius, int fluidRadius, int caveRadius, XrayConfig config) {
        List<SectionData> espSections = new ArrayList<>();
        Map<Long, List<SectionData>>[] perLayer = newLayerMaps();
        Set<Long> largeCaves = config.caveLargeOnly && caveRadius > 0 ? CaveRegions.large(config.caveMinVolume) : null;
        for (SectionData data : ScanCache.all()) {
            MeshData mesh = data.mesh();
            if (mesh == null) {
                continue;
            }
            double d = distance(data, camera);
            if (espRadius > 0 && (mesh.has(MeshData.ESP_LINES) || mesh.has(MeshData.ESP_FILL)) && d <= espRadius + 14) {
                espSections.add(data);
            }
            if (fluidRadius > 0 && d <= fluidRadius + 14) {
                add(perLayer, MeshData.FLUID_LINES, data);
                add(perLayer, MeshData.FLUID_FILL, data);
            }
            if (caveRadius > 0 && d <= caveRadius + 14 && mesh.has(MeshData.CAVE_LINES) && (largeCaves == null || largeCaves.contains(data.key))) {
                add(perLayer, MeshData.CAVE_LINES, data);
            }
        }
        // Block limit: the nearest sections' blocks first.
        espSections.sort((a, b) -> Double.compare(distance(a, camera), distance(b, camera)));
        int blocks = 0;
        for (SectionData data : espSections) {
            if (blocks >= config.blockLimit) {
                break;
            }
            blocks += data.mesh().espBlocks;
            add(perLayer, MeshData.ESP_LINES, data);
            add(perLayer, MeshData.ESP_FILL, data);
        }
        drawnEspBlocks = blocks;
        Set<Long> used = new HashSet<>();
        for (int layer = 0; layer < MeshData.LAYERS; layer++) {
            for (Map.Entry<Long, List<SectionData>> entry : perLayer[layer].entrySet()) {
                used.add(entry.getKey());
                Region region = regions.computeIfAbsent(entry.getKey(), Region::new);
                long signature = signature(entry.getValue(), layer);
                if (signature != region.built[layer]) {
                    upload(region, layer, entry.getValue());
                    region.built[layer] = signature;
                }
            }
            for (Map.Entry<Long, Region> entry : regions.entrySet()) {
                if (!perLayer[layer].containsKey(entry.getKey()) && entry.getValue().buffers[layer] != null) {
                    entry.getValue().buffers[layer].close();
                    entry.getValue().buffers[layer] = null;
                    entry.getValue().built[layer] = 0;
                }
            }
        }
        for (Iterator<Map.Entry<Long, Region>> it = regions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Region> entry = it.next();
            if (!used.contains(entry.getKey())) {
                entry.getValue().close();
                it.remove();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, List<SectionData>>[] newLayerMaps() {
        Map<Long, List<SectionData>>[] maps = new Map[MeshData.LAYERS];
        for (int i = 0; i < maps.length; i++) {
            maps[i] = new HashMap<>();
        }
        return maps;
    }

    private static void add(Map<Long, List<SectionData>>[] perLayer, int layer, SectionData data) {
        if (!data.mesh().has(layer)) {
            return;
        }
        long region = ChunkSectionPos.asLong(ChunkSectionPos.unpackX(data.key) >> REGION_SHIFT, ChunkSectionPos.unpackY(data.key) >> REGION_SHIFT,
                ChunkSectionPos.unpackZ(data.key) >> REGION_SHIFT);
        perLayer[layer].computeIfAbsent(region, k -> new ArrayList<>()).add(data);
    }

    private static long signature(List<SectionData> sections, int layer) {
        long signature = 1469598103934665603L + layer;
        for (SectionData data : sections) {
            signature += (data.key * 31 + System.identityHashCode(data.mesh())) * 0x9E3779B97F4A7C15L; // order doesn't matter
        }
        return signature == 0 ? 1 : signature;
    }

    private static double distance(SectionData data, Vec3d camera) {
        double dx = data.baseX + 8 - camera.x, dy = data.baseY + 8 - camera.y, dz = data.baseZ + 8 - camera.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Copies the chosen sections' vertices into the region's GPU buffer for one layer. */
    private static void upload(Region region, int layer, List<SectionData> sections) {
        if (allocator == null) {
            allocator = new BufferAllocator(1 << 18);
        }
        BufferBuilder builder = new BufferBuilder(allocator, LINES[layer] ? VertexFormat.DrawMode.DEBUG_LINES : VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR);
        for (SectionData data : sections) {
            int[] v = data.mesh().layers[layer];
            float ox = data.baseX - region.originX, oy = data.baseY - region.originY, oz = data.baseZ - region.originZ;
            for (int i = 0; i < v.length; i += 4) {
                builder.vertex(Float.intBitsToFloat(v[i]) + ox, Float.intBitsToFloat(v[i + 1]) + oy, Float.intBitsToFloat(v[i + 2]) + oz).color(v[i + 3]);
            }
        }
        BuiltBuffer built = builder.endNullable();
        if (built == null) {
            if (region.buffers[layer] != null) {
                region.buffers[layer].close();
                region.buffers[layer] = null;
            }
            return;
        }
        if (region.buffers[layer] == null) {
            region.buffers[layer] = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        region.buffers[layer].bind();
        region.buffers[layer].upload(built);
        VertexBuffer.unbind();
    }

    private static void draw(WorldRenderContext context, Vec3d camera, XrayConfig config, boolean esp, boolean fluid, boolean cave,
                             int espRadius, int fluidRadius, int caveRadius) {
        if (regions.isEmpty()) {
            drawCalls = 0;
            return;
        }
        ShaderProgram shader = program != null ? program : GameRenderer.getPositionColorProgram();
        if (shader == null) {
            return;
        }
        GlUniform fade = program != null ? program.getUniform("FadeRange") : null;
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        // At WorldRenderEvents.LAST the model-view matrix already holds the camera rotation: don't apply it twice.
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(config.lineWidth);
        int calls = 0;
        // Faces first, lines on top.
        int[] order = {MeshData.ESP_FILL, MeshData.FLUID_FILL, MeshData.CAVE_LINES, MeshData.FLUID_LINES, MeshData.ESP_LINES};
        for (int layer : order) {
            boolean on = layer <= MeshData.ESP_FILL ? esp : layer <= MeshData.FLUID_FILL ? fluid : cave;
            if (!on) {
                continue;
            }
            int radius = layer <= MeshData.ESP_FILL ? espRadius : layer <= MeshData.FLUID_FILL ? fluidRadius : caveRadius;
            if (fade != null) {
                if (config.fade) {
                    fade.set(radius * 0.45f, (float) radius);
                } else {
                    fade.set(0f, 0f);
                }
            }
            for (Region region : regions.values()) {
                VertexBuffer buffer = region.buffers[layer];
                if (buffer == null) {
                    continue;
                }
                Matrix4f modelView = new Matrix4f(view).translate((float) (region.originX - camera.x), (float) (region.originY - camera.y),
                        (float) (region.originZ - camera.z));
                buffer.bind();
                buffer.draw(modelView, projection, shader);
                calls++;
            }
        }
        VertexBuffer.unbind();
        drawCalls = calls;
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
