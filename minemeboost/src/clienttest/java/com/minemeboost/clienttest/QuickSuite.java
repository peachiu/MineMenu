package com.minemeboost.clienttest;

import com.mineboost.BoostConfig;
import com.mineboost.Impostors;
import com.minemenu.Feature;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;


/** A short suite for whatever is being worked on right now (fast feedback); the full suite covers everything. */
class QuickSuite extends Harness {
    QuickSuite() {
        super("quick");
    }

    @Override
    protected void build() {
        // Impostors and Sharpen measured where they matter: lots of armored mobs, little terrain (render distance 2).
        step(200, this::setupArena);
        step(5, c -> {
            on(Feature.GOD_MODE);
            c.options.getViewDistance().setValue(2);
            for (int i = 0; i < 150; i++) {
                double ang = Math.toRadians(-50 + (i * 7) % 100), d = 28 + (i % 9) * 2;
                ZombieEntity z = spawn(EntityType.ZOMBIE, Math.sin(ang) * d, -Math.cos(ang) * d, false);
                run(() -> { z.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET)); z.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    z.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS)); z.equipStack(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                    z.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)); z.setSilent(true); });
            }
            tp(0, 0, 0, 180, 5);
        });
        step(200, c -> shot(c, "measure_scene.png"));
        measure("Impostors 24", on -> BoostConfig.get().impostorDistance = on ? 24 : 0);
        step(5, c -> { BoostConfig.get().impostorDistance = 0; BoostConfig.get().renderScale = 75; });
        measure("Sharpen 50% (at Render Scale 75%)", on -> BoostConfig.get().renderScaleSharpen = on ? 50 : 0);
        step(5, c -> { BoostConfig.get().renderScale = 100; BoostConfig.get().renderScaleSharpen = 50; check("measurements done", true); });
    }

    private void measure(String name, java.util.function.Consumer<Boolean> set) {
        for (int r = 0; r < 3; r++) {
            step(60, c -> { set.accept(false); startRec(); });
            step(100, c -> stopRec(name + " OFF"));
            step(60, c -> { set.accept(true); startRec(); });
            step(100, c -> stopRec(name + " ON"));
        }
        step(5, c -> {
            int k = stats.size();
            double off = 0, on = 0, offLow = 0, onLow = 0;
            for (int i = k - 6; i < k; i += 2) { off += stats.get(i)[0]; offLow += stats.get(i)[1]; on += stats.get(i + 1)[0]; onLow += stats.get(i + 1)[1]; }
            log.add(String.format("RESULT %s: avg %.1f -> %.1f (%+.0f%%), 1%% low %.1f -> %.1f (%+.0f%%)", name, off / 3, on / 3, (on - off) * 100 / off,
                    offLow / 3, onLow / 3, (onLow - offLow) * 100 / offLow));
        });
    }
}
