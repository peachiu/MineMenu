package com.minexray.hud;

import com.minexray.EspCategory;
import com.minexray.scan.VeinMerger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/** Nearest Ore Arrow: which way (left/right of where you look, and up/down) the nearest ore vein is. */
public final class NearestOreArrow {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private NearestOreArrow() {
    }

    public static VeinMerger.Vein nearest(List<VeinMerger.Vein> veins) {
        for (VeinMerger.Vein vein : veins) {
            if (vein.category() == EspCategory.ORE) {
                return vein;
            }
        }
        return null;
    }

    /** An arrow relative to where you face: ↑ = straight ahead. */
    public static String arrow(MinecraftClient client, VeinMerger.Vein vein) {
        var camera = client.gameRenderer.getCamera();
        double dx = vein.cx() - camera.getPos().x;
        double dz = vein.cz() - camera.getPos().z;
        float target = (float) (MathHelper.atan2(dz, dx) * (180 / Math.PI)) - 90f;
        float relative = MathHelper.wrapDegrees(target - camera.getYaw());
        int index = Math.floorMod(Math.round(relative / 45f), 8);
        return ARROWS[index];
    }

    /** Blocks up (+) or down (-) from your eyes. */
    public static int dy(MinecraftClient client, VeinMerger.Vein vein) {
        return (int) Math.round(vein.cy() - client.gameRenderer.getCamera().getPos().y);
    }
}
