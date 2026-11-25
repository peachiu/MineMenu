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

/** Full suite, part 1: start-up checks, Lag Doctor, Render Scale, Safe Mode and the Player page. */
abstract class PlayerTests extends Harness {
    PlayerTests(String suite) {
        super(suite);
    }

    protected void buildPlayer() {
        step(5, c -> {
            log.add("label " + label + ", mods: " + com.mineboost.Compat.detected() + ", turned off: " + com.mineboost.Compat.turnedOff());
            // Config migration (old files written before the game started, see the run script)
            BoostConfig cfg = BoostConfig.get();
            check("Config migration: 3.3 mineboost.json kept its values (Simple Far Mobs 24, Max Entities 100)", cfg.farMobDetail == 24 && cfg.maxEntities == 100);
            check("Config migration: new fields got defaults and configVersion 2", cfg.configVersion == 2 && cfg.renderScale == 100 && cfg.renderScaleSharpen == 50 && cfg.impostorDistance == 0);
            check("cheats.json: loaded (remembered Fly from the old run file)", CheatState.isOn(Feature.FLY));
            off(Feature.FLY);
            run(() -> {
                ServerWorld w = sw();
                base = new BlockPos((int) host().getX(), 150, (int) host().getZ());
                for (BlockPos q : BlockPos.iterate(rel(-30, -3, -40), rel(30, -1, 30))) w.setBlockState(q, Blocks.STONE.getDefaultState());
                for (BlockPos q : BlockPos.iterate(rel(-30, 0, -40), rel(30, 20, 30))) w.setBlockState(q, Blocks.AIR.getDefaultState());
                server.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                w.setTimeOfDay(6000);
                host().changeGameMode(GameMode.SURVIVAL);
                host().getInventory().clear();
            });
            c.options.getViewDistance().setValue(8);
            tp(0, 0, 0, 180, 5);
            memStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        });
        step(300, c -> {});
        // ===== Lag Doctor (fixed maths) =====
        step(20, c -> LagDoctor.start(10));
        step(240, c -> {});
        step(5, c -> {
            var report = LagDoctor.lastReport();
            check("Lag Doctor: report", report != null && !report.findings().isEmpty());
            double sum = 0;
            if (report != null) {
                log.add(String.format("doctor: %d frames, %.1f ms per frame", report.frames(), report.avgMs()));
                for (var f : report.findings()) { log.add(String.format("doctor: %.2f ms %s [%s]", f.msPerFrame(), f.text(), f.fixLabel())); sum += f.msPerFrame(); }
                check("Lag Doctor: per-frame times add up to about one frame or less", sum <= report.avgMs() * 1.3);
            }
            c.setScreen(new LagDoctorScreen(null));
        });
        step(10, c -> { shot(c, "lagdoctor.png"); c.setScreen(null); });
        // ===== Render Scale screenshots (with a glowing mob: outlines must line up) =====
        step(5, c -> { a = spawn(EntityType.ZOMBIE, 0, -8, false); run(() -> ((LivingEntity) a).addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.GLOWING, 2000))); });
        step(30, c -> shot(c, "scale100.png"));
        step(5, c -> BoostConfig.get().renderScale = 75);
        step(20, c -> shot(c, "scale75.png"));
        step(5, c -> c.getWindow().toggleFullscreen());
        step(30, c -> { shot(c, "scale75_fullscreen.png"); check("Render Scale: fullscreen toggle keeps rendering", c.getCurrentFps() > 0); c.getWindow().toggleFullscreen(); });
        step(30, c -> { BoostConfig.get().renderScale = 100; clearMobs(); });
        // ===== Safe Mode =====
        step(5, c -> {
            on(Feature.FULL_BRIGHT);
            boolean ok = Guard.run(Feature.FULL_BRIGHT, () -> { throw new IllegalStateException("test failure"); });
            check("Safe Mode: failing hack turned off, game alive", !ok && !CheatState.isOn(Feature.FULL_BRIGHT));
            try {
                Path file = CheatConfig.DIR.resolve("safemode-minemenu.txt");
                Files.writeString(file, "2");
                boolean safe = com.minemenu.core.SafeMode.onStart();
                check("Safe Mode: two failed starts in a row -> the next start is safe", safe);
                Files.deleteIfExists(file);
            } catch (Exception e) { log.add("safemode file " + e); }
        });
        // ===== Freecam (debug) =====
        step(5, c -> { on(Feature.FREECAM); num = c.player.getZ(); });
        step(10, c -> { log.add("freecam after 10 ticks: active=" + Freecam.active() + " camera=" + c.getCameraEntity()); c.options.forwardKey.setPressed(true); });
        step(20, c -> {
            c.options.forwardKey.setPressed(false);
            Entity cam = c.getCameraEntity();
            log.add(String.format("freecam: active=%s cam==player=%s body moved %.2f cam dist %.2f", Freecam.active(), cam == c.player,
                    Math.abs(c.player.getZ() - num), cam.distanceTo(c.player)));
            check("Freecam: camera flew away, body stayed", Freecam.active() && cam != c.player && Math.abs(c.player.getZ() - num) < 0.5 && cam.distanceTo(c.player) > 5);
            shot(c, "freecam.png");
            off(Feature.FREECAM);
        });
        step(5, c -> check("Freecam off: camera back", c.getCameraEntity() == c.player));
        // ===== Phase (debug) =====
        step(5, c -> { run(() -> { for (BlockPos q : BlockPos.iterate(rel(-2, 0, -3), rel(2, 3, -3))) sw().setBlockState(q, Blocks.GLASS.getDefaultState()); }); tp(0, 0, 0, 180, 0); });
        step(10, c -> on(Feature.PHASE));
        step(10, c -> {
            var shape = c.world.getBlockState(rel(0, 0, -3)).getCollisionShape(c.world, rel(0, 0, -3), ShapeContext.of(c.player));
            log.add("phase: phaseOn=" + PlayerModules.phaseOn + " glass shape empty=" + shape.isEmpty());
            num = c.player.getZ();
            c.options.forwardKey.setPressed(true);
        });
        step(30, c -> {});
        step(5, c -> {
            c.options.forwardKey.setPressed(false);
            log.add(String.format("phase: walked %.1f", num - c.player.getZ()));
            check("Phase: walked through the glass wall", num - c.player.getZ() > 4);
            off(Feature.PHASE);
            clearArea();
            tp(0, 0, 0, 180, 0);
        });
        // ===== Player =====
        step(10, c -> on(Feature.DOUBLE_JUMP));
        step(5, c -> { num = c.player.getY(); maxY = num; c.options.jumpKey.setPressed(true); });
        step(3, c -> c.options.jumpKey.setPressed(false));
        step(25, c -> c.options.jumpKey.setPressed(true));
        step(1, c -> { c.options.jumpKey.setPressed(false); log.add(String.format("double jump: highest %.2f (one jump is 1.25)", maxY - num)); check("Double Jump: higher than one jump (> 1.5 blocks)", maxY - num > 1.5); maxY = Double.NaN; off(Feature.DOUBLE_JUMP); });
        step(40, c -> { on(Feature.DASH); num = c.player.getZ(); press(KeyBinds.dash); });
        step(10, c -> { log.add(String.format("dash: moved %.2f", Math.abs(c.player.getZ() - num))); check("Dash: a burst forward (> 2 blocks)", Math.abs(c.player.getZ() - num) > 2); off(Feature.DASH); });
        step(5, c -> { on(Feature.GRAVITY_CONTROL); FeatureSettings.set(Feature.GRAVITY_CONTROL, FeatureSettings.of(Feature.GRAVITY_CONTROL).get(0), 0.2); });
        step(30, c -> {
            double g = srv(() -> host().getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_GRAVITY));
            check("Gravity Control: gravity attribute at 0.2x", Math.abs(g - 0.016) < 0.002);
            off(Feature.GRAVITY_CONTROL);
        });
        step(5, c -> { run(() -> { host().getInventory().setStack(0, new ItemStack(Items.COBBLESTONE, 32)); host().getInventory().selectedSlot = 0; }); on(Feature.SCAFFOLD); tp(0, 6, 0, 180, 0); });
        step(20, c -> { check("Scaffold: a block appeared under the feet in the air", srv(() -> !sw().getBlockState(rel(0, 5, 0)).isAir())); off(Feature.SCAFFOLD); clearArea(); tp(0, 0, 0, 180, 0); });
        step(10, c -> on(Feature.LAVA_SWIM));
        step(5, c -> { run(() -> { for (BlockPos q : BlockPos.iterate(rel(-2, -2, -6), rel(2, 1, -2))) sw().setBlockState(q, Blocks.LAVA.getDefaultState()); }); tp(0, -1, -4, 180, 0); });
        step(60, c -> {});
        step(5, c -> {
            float h = srv(() -> host().getHealth());
            check("Lava Swim: no burning in lava", h >= 19 && srv(() -> host().hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE)));
            shot(c, "lava_swim.png");
            run(() -> { for (BlockPos q : BlockPos.iterate(rel(-2, -2, -6), rel(2, 1, -2))) sw().setBlockState(q, q.getY() < base.getY() ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState()); });
            clearArea(); // lava that flowed out
            tp(0, 0, 0, 180, 0);
        });
        step(20, c -> { off(Feature.LAVA_SWIM); run(() -> { host().extinguish(); host().setFireTicks(0); host().setHealth(20); }); });
        step(5, c -> { on(Feature.AUTO_SPRINT); c.options.forwardKey.setPressed(true); });
        step(10, c -> { check("Auto-Sprint: sprinting while walking", c.player.isSprinting()); c.options.forwardKey.setPressed(false); off(Feature.AUTO_SPRINT); tp(0, 0, 0, 180, 0); });
        step(5, c -> { run(() -> { for (BlockPos q : BlockPos.iterate(rel(-3, 0, -3), rel(3, 3, 3))) if (!q.equals(rel(0, 0, 0)) && !q.equals(rel(0, 1, 0))) sw().setBlockState(q, Blocks.DIRT.getDefaultState()); }); on(Feature.NUKER); });
        step(40, c -> {});
        step(5, c -> { check("Nuker: blocks around broken", srv(() -> sw().getBlockState(rel(2, 1, 2)).isAir() && sw().getBlockState(rel(-2, 2, -1)).isAir())); off(Feature.NUKER); run(() -> host().getInventory().clear()); });
        // Remote chest
        step(5, c -> action(ServerCheats.Action.OPEN_CRAFTING));
        step(10, c -> { check("Remote Chest: crafting table opens anywhere", c.currentScreen instanceof HandledScreen<?>); c.setScreen(null); action(ServerCheats.Action.OPEN_ENDER_CHEST); });
        step(10, c -> { check("Remote Chest: ender chest opens", c.currentScreen instanceof HandledScreen<?>); c.player.closeHandledScreen(); });
        step(5, c -> { set(rel(0, 0, -3), Blocks.CHEST.getDefaultState()); tp(0, 0, 0, 180, 30); });
        step(10, c -> action(ServerCheats.Action.MARK_CHEST));
        step(20, c -> tp(20, 0, 20, 0, 0));
        step(20, c -> action(ServerCheats.Action.OPEN_MARKED_CHEST));
        step(20, c -> { check("Remote Chest: marked chest opens from 20+ blocks", c.currentScreen instanceof HandledScreen<?>); c.player.closeHandledScreen(); tp(0, 0, 0, 180, 0); });
        // Morph
        step(5, c -> { CheatConfig.get().setText("morph.mob", "spider"); on(Feature.MORPH); c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK); });
        step(20, c -> { check("Morph: spider body ready", Morph.body() != null && Morph.body().getType() == EntityType.SPIDER); shot(c, "morph_spider.png"); });
        step(10, c -> { run(() -> { for (BlockPos q : BlockPos.iterate(rel(-2, 0, -2), rel(2, 6, -2))) sw().setBlockState(q, Blocks.STONE.getDefaultState()); }); tp(0, 0, 0, 180, 0); });
        step(30, c -> { num = c.player.getY(); maxY = num; c.options.forwardKey.setPressed(true); });
        step(1, c -> { c.options.forwardKey.setPressed(false); log.add(String.format("morph spider climb: %.1f", maxY - num)); check("Morph spider: climbs walls", maxY - num > 1.5); maxY = Double.NaN; clearArea(); tp(0, 0, 0, 0, 0); });
        for (String mob : new String[]{"blaze", "enderman", "creeper", "bat", "zombie"}) {
            step(5, c -> CheatConfig.get().setText("morph.mob", mob));
            step(10, c -> { check("Morph: " + mob + " body", Morph.body() != null && net.minecraft.registry.Registries.ENTITY_TYPE.getId(Morph.body().getType()).getPath().equals(mob)); shot(c, "morph_" + mob + ".png"); });
        }
        step(10, c -> CheatConfig.get().setText("morph.mob", "blaze"));
        step(3, c -> press(KeyBinds.morphPower));
        step(10, c -> check("Morph blaze power: a fireball", srv(() -> !sw().getEntitiesByClass(net.minecraft.entity.projectile.SmallFireballEntity.class, host().getBoundingBox().expand(30), e -> true).isEmpty())));
        step(5, c -> { run(() -> host().kill()); });
        step(20, c -> { check("Morph: dying turns you back", !CheatState.isOn(Feature.MORPH)); c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON); });
        step(5, c -> { if (c.player.isDead()) c.player.requestRespawn(); });
        step(40, c -> { clearArea(); clearMobs(); tp(0, 0, 0, 180, 0); run(() -> { host().changeGameMode(GameMode.SURVIVAL); host().setHealth(20); }); });
    }
}
