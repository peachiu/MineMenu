package com.minemeboost.clienttest;

import com.mineboost.BoostConfig;
import com.mineboost.BoostScreen;
import com.mineboost.doctor.LagDoctor;
import com.mineboost.doctor.LagDoctorScreen;
import com.mineboost.tools.BoostAB;
import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.MineMenuClient;
import com.minemenu.MineMenuScreen;
import com.minemenu.ServerCheats;
import com.minemenu.Waypoints;
import com.minemenu.build.BlockPlacer;
import com.minemenu.build.Clipboard;
import com.minemenu.build.GravityGun;
import com.minemenu.build.UndoStack;
import com.minemenu.combat.MindControl;
import com.minemenu.core.CheatConfig;
import com.minemenu.core.FeatureSettings;
import com.minemenu.core.Guard;
import com.minemenu.core.KeyBinds;
import com.minemenu.esp.EspModules;
import com.minemenu.esp.SectionScanner;
import com.minemenu.fun.FunEvents;
import com.minemenu.hud.BigMapScreen;
import com.minemenu.player.Freecam;
import com.minemenu.player.Morph;
import com.minemenu.player.PlayerModules;
import com.minemenu.ui.CommandPalette;
import com.minemenu.ui.MenuEntries;
import com.minemenu.ui.Profiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

/** FPS: the busy scene, the in-game A/B tool (every boost off/on twice) and the boosts it doesn't cover. */
class AbSuite extends Harness {
    AbSuite() {
        super("ab");
    }

    @Override
    protected void build() {
        buildAB();
    }

    private void buildAB() {
        step(5, c -> {
            log.add("label " + label + ", mods: " + com.mineboost.Compat.detected());
            run(() -> {
                ServerWorld w = sw();
                base = new BlockPos((int) host().getX(), 150, (int) host().getZ());
                for (BlockPos q : BlockPos.iterate(rel(-40, -3, -60), rel(40, -1, 40))) w.setBlockState(q, Blocks.STONE.getDefaultState());
                for (BlockPos q : BlockPos.iterate(rel(-40, 0, -60), rel(40, 20, 40))) w.setBlockState(q, Blocks.AIR.getDefaultState());
                server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                server.getGameRules().get(GameRules.DO_ENTITY_DROPS).set(false, server);
                w.setTimeOfDay(6000);
                host().changeGameMode(GameMode.SPECTATOR);
            });
            c.options.getViewDistance().setValue(8);
            tp(0, 2, 0, 180, 5);
        });
        step(200, c -> {
            run(() -> {
                ServerWorld w = sw();
                java.util.Random r = new java.util.Random(1);
                for (int i = 0; i < 98; i++) {
                    ZombieEntity z = EntityType.ZOMBIE.create(w);
                    double ang = Math.toRadians(-60 + r.nextDouble() * 120), d = 26 + r.nextDouble() * 18;
                    z.refreshPositionAndAngles(base.getX() + Math.sin(ang) * d, base.getY(), base.getZ() - Math.cos(ang) * d, 0, 0);
                    z.setAiDisabled(true); z.setPersistent(); z.setSilent(true);
                    z.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    z.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    z.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                    z.equipStack(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                    z.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                    z.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                    w.spawnEntity(z);
                }
                for (int i = 0; i < 40; i++) w.setBlockState(rel(-10 + (i % 10) * 2, 0, -8 - (i / 10) * 4), Blocks.CHEST.getDefaultState());
                for (int i = 0; i < 150; i++) {
                    ItemEntity it = new ItemEntity(w, base.getX() - 6 + r.nextDouble() * 12, base.getY(), base.getZ() - 5 - r.nextDouble() * 7, new ItemStack(net.minecraft.registry.Registries.ITEM.get(1 + r.nextInt(800))));
                    it.setNeverDespawn(); it.setPickupDelayInfinite(); it.setVelocity(0, 0, 0);
                    w.spawnEntity(it);
                }
                // 300 mobs with AI 60-90 blocks behind you, for Far Entity Ticks
                for (int i = 0; i < 300; i++) {
                    var cow = EntityType.COW.create(w);
                    cow.refreshPositionAndAngles(base.getX() - 20 + r.nextDouble() * 40, base.getY(), base.getZ() + 25 + r.nextDouble() * 14, 0, 0);
                    cow.setPersistent(); cow.setSilent(true);
                    w.spawnEntity(cow);
                }
            });
        });
        step(200, c -> shot(c, "scene.png"));
        step(5, c -> BoostAB.start(c));
        step(20, c -> {});
        // wait for the A/B tool (about 7 minutes); then read its table
        for (int i = 0; i < 60; i++) step(200, c -> {});
        step(5, c -> {
            log.add("A/B finished: " + !BoostAB.running());
            try { log.addAll(Files.readAllLines(CheatConfig.DIR.resolve("benchmark-results.md"))); } catch (Exception e) { log.add("no table " + e); }
        });
        // Not in the A/B tool: Far Entity Ticks, Smooth Fog, Battery Saver, Dynamic Render Scale
        String[] names = {"Far Entity Ticks", "Smooth Fog"};
        for (int k = 0; k < names.length; k++) {
            int kk = k;
            for (int r = 0; r < 3; r++) {
                step(30, c -> { set(kk, false); startRec(); });
                step(100, c -> stopRec(names[kk] + " OFF"));
                step(30, c -> { set(kk, true); startRec(); });
                step(100, c -> stopRec(names[kk] + " ON"));
            }
            step(5, c -> { result(names[kk]); set(kk, false); });
        }
        step(30, c -> { BoostConfig.get().batterySaver = true; startRec(); });
        step(100, c -> { double[] r = stopRec("Battery Saver ON"); check("Battery Saver: FPS capped (<= 31)", r[0] <= 31); BoostConfig.get().batterySaver = false; });
        step(30, c -> { BoostConfig.get().fpsTarget = 60; BoostConfig.get().dynamicRenderScale = true; startRec(); });
        step(200, c -> { stopRec("Dynamic Render Scale (target 60)"); log.add("dynamic scale now " + com.mineboost.renderscale.RenderScale.currentScale() + "%"); BoostConfig.get().dynamicRenderScale = false; BoostConfig.get().fpsTarget = 0; });
        step(5, c -> {});
    }

    private void set(int k, boolean on) {
        if (k == 0) BoostConfig.get().farEntityTicks = on; else BoostConfig.get().smoothFog = on;
    }

    private void result(String name) {
        int k = stats.size();
        double off = 0, on = 0, offLow = 0, onLow = 0;
        for (int i = k - 6; i < k; i += 2) { off += stats.get(i)[0]; offLow += stats.get(i)[1]; on += stats.get(i + 1)[0]; onLow += stats.get(i + 1)[1]; }
        log.add(String.format("RESULT %s: avg %.1f -> %.1f (%+.0f%%), 1%% low %.1f -> %.1f (%+.0f%%)", name, off / 3, on / 3, (on - off) * 100 / off, offLow / 3, onLow / 3, (onLow - offLow) * 100 / offLow));
    }

}
