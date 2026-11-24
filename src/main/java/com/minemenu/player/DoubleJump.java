package com.minemenu.player;

import com.minemenu.Feature;
import com.minemenu.core.CheatModule;
import com.minemenu.core.FeatureSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Double Jump: press jump again in mid-air (1 to 3 times). The server forgets the fall while you go up. */
public final class DoubleJump implements CheatModule {
    private int used;
    private boolean jumpWasDown;
    private double lastServerY = Double.NaN;

    @Override
    public void clientTick(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        boolean down = client.options.jumpKey.isPressed();
        boolean pressed = down && !jumpWasDown;
        jumpWasDown = down;
        if (p.isOnGround() || p.isClimbing() || p.isTouchingWater() || p.getAbilities().flying) {
            used = 0;
            return;
        }
        int max = (int) FeatureSettings.get(Feature.DOUBLE_JUMP, "jumps");
        if (pressed && used < max && !p.isFallFlying()) {
            used++;
            Vec3d v = p.getVelocity();
            Vec3d look = Vec3d.fromPolar(0, p.getYaw());
            double push = p.isSprinting() ? 0.2 : 0.05;
            p.setVelocity(v.x + look.x * push, 0.42 + p.getJumpBoostVelocityModifier(), v.z + look.z * push);
            p.fallDistance = 0;
        }
    }

    @Override
    public void serverTick(ServerPlayerEntity player, int tick) {
        if (!Double.isNaN(lastServerY) && player.getY() > lastServerY + 0.01) {
            player.fallDistance = 0; // going up again: the fall so far doesn't count
        }
        lastServerY = player.getY();
    }
}
