package com.minemenu.hud;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.MineMenuClient;
import com.minemenu.core.FeatureSettings;
import com.minemenu.core.Guard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/** Mob Health Bars: "❤ 12/20" above every mob within range, green to red. Singleplayer only. */
public final class HealthBars {
    /** Refreshed every client tick; read for every mob drawn, so a plain flag. */
    public static volatile boolean on;
    private static double rangeSq;

    private HealthBars() {
    }

    public static void refresh(MinecraftClient client) {
        on = CheatState.isOn(Feature.MOB_HEALTH_BARS) && (MineMenuClient.isSingleplayer(client) || Feature.MOB_HEALTH_BARS.allowedInMultiplayer());
        double range = on ? FeatureSettings.get(Feature.MOB_HEALTH_BARS, "range") : 0;
        rangeSq = range * range;
    }

    public static void render(LivingEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (entity instanceof PlayerEntity || entity instanceof ArmorStandEntity || entity.isInvisible()
                || client.getEntityRenderDispatcher().getSquaredDistanceToCamera(entity) > rangeSq) {
            return;
        }
        Guard.run(Feature.MOB_HEALTH_BARS, () -> {
            Vec3d anchor = entity.getAttachments().getPointNullable(EntityAttachmentType.NAME_TAG, 0, entity.getYaw(tickDelta));
            if (anchor == null) {
                return;
            }
            float health = entity.getHealth();
            float max = entity.getMaxHealth();
            float fraction = max <= 0 ? 0 : Math.min(1, health / max);
            int color = fraction > 0.6f ? 0x55FF55 : fraction > 0.3f ? 0xFFFF55 : 0xFF5555;
            Text text = Text.literal("❤ " + Math.round(health) + "/" + Math.round(max));
            boolean named = entity.hasCustomName() && entity.isCustomNameVisible();
            matrices.push();
            matrices.translate(anchor.x, anchor.y + (named ? 0.75 : 0.5), anchor.z);
            matrices.multiply(client.getEntityRenderDispatcher().getRotation());
            matrices.scale(0.025f, -0.025f, 0.025f);
            TextRenderer font = client.textRenderer;
            font.draw(text, -font.getWidth(text) / 2f, 0, color, false, matrices.peek().getPositionMatrix(), consumers,
                    TextRenderer.TextLayerType.NORMAL, 0x40000000, light);
            matrices.pop();
        });
    }
}
