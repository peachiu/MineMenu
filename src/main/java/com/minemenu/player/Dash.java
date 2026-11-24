package com.minemenu.player;

import com.minemenu.Feature;
import com.minemenu.core.FeatureSettings;
import com.minemenu.core.KeyBinds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;

/** Dash: the Dash key (V) gives a burst in the direction you're moving (or looking), with a cooldown. */
public final class Dash {
    private static long lastDash;

    private Dash() {
    }

    public static void tick(MinecraftClient client) {
        while (KeyBinds.dash.wasPressed()) {
            long now = Util.getMeasuringTimeMs();
            long cooldown = (long) (FeatureSettings.get(Feature.DASH, "cooldown") * 1000);
            if (now - lastDash < cooldown) {
                continue;
            }
            lastDash = now;
            ClientPlayerEntity p = client.player;
            double forward = (client.options.forwardKey.isPressed() ? 1 : 0) - (client.options.backKey.isPressed() ? 1 : 0);
            double strafe = (client.options.leftKey.isPressed() ? 1 : 0) - (client.options.rightKey.isPressed() ? 1 : 0);
            Vec3d look = Vec3d.fromPolar(0, p.getYaw());
            Vec3d left = new Vec3d(look.z, 0, -look.x);
            Vec3d dir = forward == 0 && strafe == 0 ? look : look.multiply(forward).add(left.multiply(strafe)).normalize();
            double strength = FeatureSettings.get(Feature.DASH, "strength");
            p.setVelocity(p.getVelocity().add(dir.x * strength, 0.15, dir.z * strength));
        }
    }
}
