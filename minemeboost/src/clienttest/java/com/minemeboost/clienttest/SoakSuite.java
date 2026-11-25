package com.minemeboost.clienttest;

import com.mineboost.BoostConfig;
import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.ServerCheats;
import com.minemenu.core.CheatConfig;
import com.minemenu.core.FeatureSettings;
import com.minemenu.fun.FunEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Soak test: N minutes (15 by default, 60 in the weekly CI run) of Chaos Mode, Minimap, Ore Vision, Chest ESP,
 * Health Bars and Impostors, with a round of heavier things every 2 minutes (mob war, a 32x32x32 fill and undo,
 * morphs, meteor, tornado, freecam, Render Scale changes). Fails if anything was caught by Safe Mode, if memory
 * after a GC keeps growing, or if the game stops ticking.
 */
class SoakSuite extends Harness {
    private final int minutes;
    private final List<Long> memory = new ArrayList<>();
    private final String[] mobs = {"spider", "blaze", "enderman", "bat", "zombie", "creeper"};

    SoakSuite(int minutes) {
        super("soak");
        this.minutes = Math.max(1, minutes);
    }

    private static long usedAfterGc() {
        System.gc();
        Runtime r = Runtime.getRuntime();
        return (r.totalMemory() - r.freeMemory()) >> 20;
    }

    @Override
    protected void build() {
        step(200, this::setupArena);
        step(5, c -> {
            log.add("soak: " + minutes + " minutes");
            on(Feature.GOD_MODE);
            on(Feature.MINIMAP);
            on(Feature.INFO_HUD);
            on(Feature.ORE_VISION);
            on(Feature.CHEST_ESP);
            on(Feature.MOB_HEALTH_BARS);
            on(Feature.CHAOS_MODE);
            FeatureSettings.set(Feature.CHAOS_MODE, FeatureSettings.of(Feature.CHAOS_MODE).get(0), 1); // an event every minute
            BoostConfig.get().impostorDistance = 24;
            run(() -> {
                for (int i = 0; i < 20; i++) sw().setBlockState(rel(-10 + i, -2, -12), (i % 3 == 0 ? Blocks.DIAMOND_ORE : Blocks.CHEST).getDefaultState());
            });
        });
        int rounds = minutes / 2;
        for (int r = 0; r < Math.max(1, rounds); r++) {
            int round = r;
            // ~2 minutes per round = 2400 ticks
            step(20, c -> {
                for (int i = 0; i < 8; i++) {
                    ZombieEntity z = spawn(EntityType.ZOMBIE, -8 + i * 2, -10, true);
                    run(() -> z.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)));
                }
                for (int i = 0; i < 10; i++) spawn(EntityType.COW, -12 + i * 3, -34, false);
            });
            step(20, c -> action(ServerCheats.Action.MOB_WAR));
            step(300, c -> {});
            step(5, c -> { run(() -> { com.minemenu.build.Selection.set(1, rel(-16, 0, 8), host()); com.minemenu.build.Selection.set(2, rel(15, 31, 39), host()); host().getInventory().setStack(0, new ItemStack(Items.STONE)); host().getInventory().selectedSlot = 0; }); action(ServerCheats.Action.FILL_SELECTION); });
            step(200, c -> action(ServerCheats.Action.UNDO));
            step(200, c -> { CheatConfig.get().setText("morph.mob", mobs[round % mobs.length]); on(Feature.MORPH); });
            step(200, c -> off(Feature.MORPH));
            step(5, c -> run(() -> FunEvents.meteor(host(), Vec3d.ofCenter(rel(0, 0, -20)))));
            step(200, c -> action(ServerCheats.Action.TORNADO));
            step(300, c -> { on(Feature.FREECAM); BoostConfig.get().renderScale = round % 2 == 0 ? 75 : 50; });
            step(200, c -> { off(Feature.FREECAM); BoostConfig.get().renderScale = 100; });
            step(200, c -> { clearMobs(); run(() -> { sw().setTimeOfDay(6000); host().setHealth(20); host().getHungerManager().setFoodLevel(20); }); tp(0, 0, 0, 180, 5); });
            step(400, c -> {
                long used = usedAfterGc();
                memory.add(used);
                log.add(String.format("soak round %d: memory after GC %d MB, FPS %d, chaos %s", round + 1, used, c.getCurrentFps(), CheatState.isOn(Feature.CHAOS_MODE)));
            });
        }
        step(5, c -> {
            for (Feature f : Feature.values()) off(f);
            BoostConfig.get().impostorDistance = 0;
            check("Soak: the game kept running for " + minutes + " minutes", c.player != null && c.world != null);
            // Memory: the last rounds compared with the first ones (after warm-up), both after a GC.
            if (memory.size() >= 4) {
                long first = Math.min(memory.get(1), memory.get(2));
                long last = Math.min(memory.get(memory.size() - 1), memory.get(memory.size() - 2));
                log.add("soak memory: " + memory);
                check("Soak: no steady memory growth (< 200 MB more than after warm-up)", last - first < 200);
            }
            try {
                long errors = Files.readAllLines(Path.of("logs", "latest.log")).stream()
                        .filter(l -> l.contains("[Safe Mode]") && !l.contains("didn't start properly")).count();
                log.add("soak: Safe Mode messages in the log: " + errors);
                check("Soak: no errors caught by Safe Mode", errors == 0);
            } catch (Exception e) {
                log.add("ERROR reading the log: " + e);
            }
        });
    }
}
