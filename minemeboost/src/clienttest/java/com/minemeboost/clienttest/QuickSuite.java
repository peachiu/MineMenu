package com.minemeboost.clienttest;

import com.mineboost.BoostConfig;
import com.mineboost.Impostors;
import com.minemenu.Feature;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.nio.file.Path;

/** A short suite for whatever is being worked on right now (fast feedback); the full suite covers everything. */
class QuickSuite extends Harness {
    QuickSuite() {
        super("quick");
    }

    @Override
    protected void build() {
        step(200, this::setupArena);
        step(5, c -> {
            on(Feature.GOD_MODE);
            for (int i = 0; i < 16; i++) {
                ZombieEntity z = spawn(EntityType.ZOMBIE, -12 + i * 1.6, -32 - (i % 4) * 2, false);
                if (i % 2 == 0) run(() -> { z.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET)); z.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)); });
            }
            for (int i = 0; i < 6; i++) spawn(EntityType.COW, -10 + i * 4, -28, false);
            spawn(EntityType.CREEPER, 6, -26, false);
            tp(0, 0, 0, 180, 5);
        });
        step(40, c -> shot(c, "impostors_off.png"));
        step(5, c -> BoostConfig.get().impostorDistance = 24);
        for (int i = 0; i < 6; i++) step(10, c -> log.add("impostors: enabled " + Impostors.enabled() + ", waiting " + Impostors.waiting() + ", pictures " + Impostors.count()));
        step(5, c -> {
            shot(c, "impostors_on.png");
            check("Impostors: pictures taken", Impostors.count() >= 3);
            try { Impostors.savePictures(Path.of("clienttest", "impostors")); } catch (Exception e) { log.add("save: " + e); }
        });
        step(5, c -> {});
    }
}
