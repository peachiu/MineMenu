package com.mineboost;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Impostors: mobs farther than {@link BoostConfig#impostorDistance} are drawn as one flat picture of themselves
 * (a "billboard" that turns towards you) instead of the full model with armor and held items. Each kind of mob
 * (type, baby or not, equipment, variant) is photographed once from 8 sides into a small texture; the side that
 * matches the way the mob faces you is shown. Far mobs don't animate (they look like a still frame), which is why
 * it starts at 24 blocks or more. Players, bosses, glowing, burning, ridden and invisible mobs are always drawn
 * normally. Off with Iris (shader packs draw entities their own way).
 */
public final class Impostors {
    private static final int SIZE = 64;   // pixels per side view
    private static final int VIEWS = 8;
    private static final int MAX_KINDS = 96;
    private static final int BAKES_PER_TICK = 2;

    private static final class Impostor {
        final SimpleFramebuffer framebuffer;
        final Identifier texture;
        final float side;   // world size of the square picture
        final float bottom; // how far below the feet the picture starts
        long lastUsed;

        Impostor(SimpleFramebuffer framebuffer, Identifier texture, float side, float bottom) {
            this.framebuffer = framebuffer;
            this.texture = texture;
            this.side = side;
            this.bottom = bottom;
        }
    }

    /** The framebuffer's colour texture, made available to entity render layers under an Identifier. */
    private static final class FramebufferTexture extends AbstractTexture {
        FramebufferTexture(int glId) {
            this.glId = glId;
        }

        @Override
        public void load(ResourceManager manager) {
        }

        @Override
        public void clearGlId() {
            // owned (and deleted) by the framebuffer
        }
    }

    private static final Map<String, Impostor> impostors = new HashMap<>();
    private static final Map<String, WeakReference<LivingEntity>> toBake = new HashMap<>();
    private static final Deque<String> bakeOrder = new ArrayDeque<>();
    private static long frame;
    private static int nextId;

    private Impostors() {
    }

    public static boolean enabled() {
        return BoostConfig.get().impostorDistance > 0 && !Compat.IRIS;
    }

    /**
     * Called from EntityRenderDispatcher.render. Draws the impostor and returns true, or returns false (and asks
     * for a picture to be taken if there isn't one yet) so the mob is drawn normally.
     */
    public static boolean draw(Entity entity, double x, double y, double z, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (!(entity instanceof LivingEntity living) || !applies(living)) {
            return false;
        }
        double distance = BoostConfig.get().impostorDistance;
        if (x * x + y * y + z * z < distance * distance) {
            return false;
        }
        String key = key(living);
        Impostor impostor = impostors.get(key);
        if (impostor == null) {
            if (!toBake.containsKey(key) && living.hurtTime == 0 && living.deathTime == 0) {
                toBake.put(key, new WeakReference<>(living));
                bakeOrder.add(key);
            }
            return false;
        }
        impostor.lastUsed = frame;
        // Which of the 8 pictures: the mob's facing compared with the direction it is seen from.
        // View k was photographed with the mob turned by k*45 degrees, seen from the south.
        double viewYaw = Math.toDegrees(Math.atan2(-x, z)); // yaw (Minecraft convention) of the camera-to-mob direction
        int view = Math.floorMod(Math.round((float) ((viewYaw - living.bodyYaw - 180) / (360.0 / VIEWS))), VIEWS);
        float u0 = (float) view / VIEWS;
        float u1 = (float) (view + 1) / VIEWS;

        matrices.push();
        matrices.translate(x, y, z);
        // Turn the picture towards the camera (around the vertical axis only, so mobs stay upright).
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) Math.atan2(-x, -z)));
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(impostor.texture));
        float half = impostor.side / 2;
        float y0 = -impostor.bottom;
        float y1 = y0 + impostor.side;
        // Framebuffer textures are stored bottom-up: v = 0 is the bottom of the picture.
        buffer.vertex(m, -half, y0, 0).color(255, 255, 255, 255).texture(u0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        buffer.vertex(m, half, y0, 0).color(255, 255, 255, 255).texture(u1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        buffer.vertex(m, half, y1, 0).color(255, 255, 255, 255).texture(u1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        buffer.vertex(m, -half, y1, 0).color(255, 255, 255, 255).texture(u0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        matrices.pop();
        return true;
    }

    private static boolean applies(LivingEntity living) {
        return !(living instanceof PlayerEntity || living instanceof EnderDragonEntity || living instanceof WitherEntity)
                && !living.isGlowing() && !living.isInvisible() && !living.isOnFire() && !living.hasPassengers()
                && !living.hasVehicle() && living.deathTime == 0;
    }

    /** What makes two mobs look the same from far away. */
    private static String key(LivingEntity living) {
        StringBuilder key = new StringBuilder(Registries.ENTITY_TYPE.getId(living.getType()).toString());
        if (living.isBaby()) {
            key.append("|baby");
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = living.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                key.append('|').append(slot.ordinal()).append(Registries.ITEM.getId(stack.getItem()).getPath());
            }
        }
        if (living instanceof net.minecraft.entity.VariantHolder<?> variant) {
            key.append("|v").append(variant.getVariant());
        }
        if (living instanceof net.minecraft.entity.passive.SheepEntity sheep) {
            key.append("|c").append(sheep.getColor().getId()).append(sheep.isSheared() ? "s" : "");
        }
        return key.toString();
    }

    /** Client tick (between frames): photograph a couple of new kinds of mob, forget unused ones. */
    public static void tick(MinecraftClient client) {
        frame++;
        if (!enabled() || client.world == null) {
            if (!impostors.isEmpty() && (!enabled() || client.world == null)) {
                clear();
            }
            return;
        }
        for (int i = 0; i < BAKES_PER_TICK && !bakeOrder.isEmpty(); i++) {
            String key = bakeOrder.poll();
            LivingEntity living = toBake.remove(key).get();
            if (living != null && !living.isRemoved() && living.getWorld() == client.world) {
                BoostGuard.run("Impostors", () -> bake(client, key, living), () -> BoostConfig.get().impostorDistance = 0);
            }
        }
        // Over the limit: drop the ones not seen for the longest.
        while (impostors.size() > MAX_KINDS) {
            String oldest = null;
            long oldestFrame = Long.MAX_VALUE;
            for (Map.Entry<String, Impostor> e : impostors.entrySet()) {
                if (e.getValue().lastUsed < oldestFrame) {
                    oldestFrame = e.getValue().lastUsed;
                    oldest = e.getKey();
                }
            }
            delete(client, impostors.remove(oldest));
        }
    }

    private static void bake(MinecraftClient client, String key, LivingEntity living) {
        float scale = living.getScale();
        float height = living.getHeight();
        float width = living.getWidth();
        // A square that fits the mob from every side, with a little room for arms, weapons and wings.
        float side = Math.max(height, width * 1.6f) * 1.25f + 0.3f * scale;
        float bottom = side * 0.06f;

        SimpleFramebuffer framebuffer = new SimpleFramebuffer(SIZE * VIEWS, SIZE, true, MinecraftClient.IS_SYSTEM_MAC);
        framebuffer.setClearColor(0, 0, 0, 0);
        framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
        framebuffer.setTexFilter(GL11.GL_LINEAR);

        // Pose: standing straight, looking ahead (restored afterwards).
        float bodyYaw = living.bodyYaw, prevBodyYaw = living.prevBodyYaw, headYaw = living.headYaw, prevHeadYaw = living.prevHeadYaw;
        float yaw = living.getYaw(), prevYaw = living.prevYaw, pitch = living.getPitch(), prevPitch = living.prevPitch;
        living.bodyYaw = living.prevBodyYaw = living.headYaw = living.prevHeadYaw = 0;
        living.setYaw(0);
        living.prevYaw = 0;
        living.setPitch(0);
        living.prevPitch = 0;

        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-side / 2, side / 2, -bottom, side - bottom, -100, 100), VertexSorter.BY_Z);
        DiffuseLighting.method_34742();
        RenderSystem.enableDepthTest();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        var renderer = client.getEntityRenderDispatcher().getRenderer(living);
        try {
            framebuffer.beginWrite(false);
            for (int view = 0; view < VIEWS; view++) {
                RenderSystem.viewport(view * SIZE, 0, SIZE, SIZE);
                MatrixStack matrices = new MatrixStack();
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(view * (360f / VIEWS)));
                renderer.render(living, 0, 1.0f, matrices, immediate, LightmapTextureManager.MAX_LIGHT_COORDINATE);
                immediate.draw();
            }
        } finally {
            living.bodyYaw = bodyYaw;
            living.prevBodyYaw = prevBodyYaw;
            living.headYaw = headYaw;
            living.prevHeadYaw = prevHeadYaw;
            living.setYaw(yaw);
            living.prevYaw = prevYaw;
            living.setPitch(pitch);
            living.prevPitch = prevPitch;
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            DiffuseLighting.enableGuiDepthLighting();
            client.getFramebuffer().beginWrite(true);
        }
        Identifier texture = Identifier.of("mineboost", "impostor/" + (nextId++));
        client.getTextureManager().registerTexture(texture, new FramebufferTexture(framebuffer.getColorAttachment()));
        Impostor impostor = new Impostor(framebuffer, texture, side, bottom);
        impostor.lastUsed = frame;
        Impostor old = impostors.put(key, impostor);
        if (old != null) {
            delete(client, old);
        }
    }

    private static void delete(MinecraftClient client, Impostor impostor) {
        if (impostor == null) {
            return;
        }
        client.getTextureManager().destroyTexture(impostor.texture);
        impostor.framebuffer.delete();
    }

    /** Leaving a world, turning it off: free every picture. */
    public static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Iterator<Impostor> it = impostors.values().iterator(); it.hasNext(); ) {
            delete(client, it.next());
            it.remove();
        }
        toBake.clear();
        bakeOrder.clear();
    }

    /** For tests: how many kinds of mob have a picture. */
    public static int count() {
        return impostors.size();
    }

    /** For tests: saves every picture strip as a PNG (render thread). */
    public static void savePictures(java.nio.file.Path dir) throws java.io.IOException {
        java.nio.file.Files.createDirectories(dir);
        for (Map.Entry<String, Impostor> e : impostors.entrySet()) {
            try (var image = net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(e.getValue().framebuffer)) {
                image.writeTo(dir.resolve(e.getKey().replaceAll("[^a-zA-Z0-9_.-]", "_") + ".png"));
            }
        }
    }
}
