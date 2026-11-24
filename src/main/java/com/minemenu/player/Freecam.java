package com.minemenu.player;

import com.minemenu.Feature;
import com.minemenu.core.CheatModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Freecam: the camera becomes a separate (invisible, never added to the world) marker entity that you fly with
 * WASD, space and sneak, while your real body stands still and is drawn where it is. The mouse turns the camera
 * (see the freecam part of MouseMixin). Switching off, dying or leaving puts the camera back on you.
 */
public final class Freecam implements CheatModule {
    private static MarkerEntity camera;
    private static ClientPlayerEntity owner;

    public static boolean active() {
        return camera != null;
    }

    public static MarkerEntity camera() {
        return camera;
    }

    @Override
    public void clientTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (camera == null || owner != player || camera.getWorld() != client.world) {
            stop(client);
            start(client, player);
        }
        // Your body stands still.
        player.input = new Input();
        double speed = client.options.sprintKey.isPressed() ? 1.2 : 0.5;
        Vec3d look = Vec3d.fromPolar(0, camera.getYaw());
        double forward = (client.options.forwardKey.isPressed() ? 1 : 0) - (client.options.backKey.isPressed() ? 1 : 0);
        double strafe = (client.options.leftKey.isPressed() ? 1 : 0) - (client.options.rightKey.isPressed() ? 1 : 0);
        double up = (client.options.jumpKey.isPressed() ? 1 : 0) - (client.options.sneakKey.isPressed() ? 1 : 0);
        Vec3d left = new Vec3d(look.z, 0, -look.x);
        Vec3d move = look.multiply(forward).add(left.multiply(strafe)).add(0, up, 0);
        camera.prevX = camera.getX();
        camera.prevY = camera.getY();
        camera.prevZ = camera.getZ();
        camera.lastRenderX = camera.getX();
        camera.lastRenderY = camera.getY();
        camera.lastRenderZ = camera.getZ();
        camera.prevYaw = camera.getYaw();
        camera.prevPitch = camera.getPitch();
        if (move.lengthSquared() > 0) {
            Vec3d step = move.normalize().multiply(speed);
            camera.setPosition(camera.getX() + step.x, camera.getY() + step.y, camera.getZ() + step.z);
        }
    }

    private static void start(MinecraftClient client, ClientPlayerEntity player) {
        camera = new MarkerEntity(EntityType.MARKER, client.world);
        camera.refreshPositionAndAngles(player.getX(), player.getEyeY() - camera.getStandingEyeHeight(), player.getZ(), player.getYaw(), player.getPitch());
        camera.prevX = camera.getX();
        camera.prevY = camera.getY();
        camera.prevZ = camera.getZ();
        owner = player;
        client.setCameraEntity(camera);
    }

    public static void stop(MinecraftClient client) {
        if (camera == null) {
            return;
        }
        camera = null;
        if (client.player != null) {
            client.setCameraEntity(client.player);
            client.player.input = new KeyboardInput(client.options);
        }
        owner = null;
    }

    @Override
    public void reset() {
        stop(MinecraftClient.getInstance());
    }

    /** Called every client tick: if Freecam was turned off (menu, key, Safe Mode), give the camera back. */
    public static void checkOff(MinecraftClient client) {
        if (camera != null && !com.minemenu.CheatState.isOn(Feature.FREECAM)) {
            stop(client);
        }
    }
}
