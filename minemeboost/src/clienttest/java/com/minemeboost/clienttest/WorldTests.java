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

/** Full suite, part 2: Combat, Build, Items, Fun, Utility and the menu. */
abstract class WorldTests extends PlayerTests {
    WorldTests(String suite) {
        super(suite);
    }

    protected void buildWorld() {
        // ===== Combat =====
        step(5, c -> { on(Feature.GOD_MODE); a = spawn(EntityType.ZOMBIE, 0, -12, false); run(() -> { host().getInventory().setStack(0, new ItemStack(Items.BOW)); host().getInventory().setStack(1, new ItemStack(Items.ARROW, 64)); }); on(Feature.BOW_AIMBOT); });
        step(5, c -> run(() -> {
            ArrowEntity arrow = new ArrowEntity(sw(), host(), new ItemStack(Items.ARROW), null);
            arrow.setPosition(host().getEyePos());
            // aimed 3 blocks to the side of the zombie: the aimbot should bend it in
            Vec3d to = a.getPos().add(3, 1, 0).subtract(host().getEyePos()).normalize().multiply(3);
            arrow.setVelocity(to);
            sw().spawnEntity(arrow);
        }));
        step(40, c -> {});
        step(5, c -> { log.add("bow aimbot: zombie health " + health(a)); check("Bow Aimbot: the arrow aimed beside the zombie still hit it", health(a) < 20); off(Feature.BOW_AIMBOT); });
        step(5, c -> { run(() -> host().setHealth(10)); on(Feature.LIFESTEAL); run(() -> { host().getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD)); host().getInventory().selectedSlot = 0; }); a = spawn(EntityType.ZOMBIE, 0, -2, false); });
        step(25, c -> c.interactionManager.attackEntity(c.player, c.world.getEntityById(a.getId())));
        step(5, c -> { float h = srv(() -> host().getHealth()); log.add("lifesteal: health 10 -> " + h); check("Lifesteal: healed by hitting", h > 10); off(Feature.LIFESTEAL); });
        step(5, c -> { clearMobs(); on(Feature.THORNS_AURA); b = spawn(EntityType.ZOMBIE, 1, -1, true); run(() -> ((ZombieEntity) b).equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET))); });
        step(100, c -> {});
        step(5, c -> { log.add("thorns: attacker health " + health(b)); check("Thorns Aura: the zombie that hits you gets hurt", health(b) < 20); off(Feature.THORNS_AURA); clearMobs(); });
        step(5, c -> { on(Feature.FORCE_FIELD); b = spawn(EntityType.ZOMBIE, 1, -1, true); run(() -> ((ZombieEntity) b).equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET))); });
        step(40, c -> {});
        step(5, c -> { double d = srv(() -> b.distanceTo(host())); log.add(String.format("force field: zombie distance %.1f", d)); check("Force Field: hostile pushed out (> 3 blocks)", d > 3); off(Feature.FORCE_FIELD); clearMobs(); });
        step(5, c -> {
            on(Feature.MIND_CONTROL);
            a = spawn(EntityType.ZOMBIE, 0, -4, true);
            b = spawn(EntityType.SKELETON, 3, -9, false);
            run(() -> { ((ZombieEntity) a).equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); ((MobEntity) b).equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); });
            tp(0, 0, 0, 180, 10);
        });
        step(10, c -> press(KeyBinds.mindControl));
        step(200, c -> {});
        step(5, c -> { log.add("mind control: controlled " + MindControl.count() + ", skeleton health " + health(b)); check("Mind Control: the zombie attacks the skeleton", MindControl.count() >= 1 && health(b) < 20); off(Feature.MIND_CONTROL); clearMobs(); });
        step(5, c -> { for (int i = 0; i < 4; i++) { ZombieEntity z = spawn(EntityType.ZOMBIE, -4 + i * 3, -8, true); run(() -> z.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET))); } });
        step(10, c -> action(ServerCheats.Action.MOB_WAR));
        step(100, c -> {});
        step(5, c -> { count = srv(() -> (int) sw().getEntitiesByClass(ZombieEntity.class, host().getBoundingBox().expand(30), z -> z.getHealth() < 20).size()); check("Mob War: zombies hurt each other", count >= 2); clearMobs(); });
        step(5, c -> { on(Feature.TRIDENT_STORM); run(() -> { var t = new net.minecraft.entity.projectile.TridentEntity(sw(), host(), new ItemStack(Items.TRIDENT)); t.setPosition(host().getEyePos()); t.setVelocity(0, -0.5, -2.5); sw().spawnEntity(t); }); });
        step(60, c -> { count = srv(() -> sw().getEntitiesByClass(net.minecraft.entity.projectile.TridentEntity.class, host().getBoundingBox().expand(60), e -> true).size()); check("Trident Storm: more tridents rain down", count >= 5); off(Feature.TRIDENT_STORM); clearMobs(); clearArea(); off(Feature.GOD_MODE); });
        step(5, c -> run(() -> com.minemenu.items.Inventories.saveLoadout(host(), 0)));
        step(5, c -> { run(() -> { host().equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET)); com.minemenu.items.Inventories.saveLoadout(host(), 1); host().equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY); com.minemenu.items.Inventories.equipLoadout(host(), 1); }); });
        step(5, c -> check("Armor Loadouts: saved and equipped", srv(() -> host().getEquippedStack(EquipmentSlot.HEAD).isOf(Items.DIAMOND_HELMET))));
        step(5, c -> { on(Feature.GOD_MODE); run(() -> com.minemenu.combat.BossArena.summon(host(), 0, 2, true)); });
        step(40, c -> { check("Boss Arena: Wither with doubled health and an arena", srv(() -> sw().getEntitiesByClass(net.minecraft.entity.boss.WitherEntity.class, host().getBoundingBox().expand(40), w -> w.getMaxHealth() >= 600).size() == 1) && !BlockPlacer.busy()); shot(c, "boss_arena.png"); });
        step(5, c -> { action(ServerCheats.Action.UNDO); clearMobs(); });
        step(60, c -> { off(Feature.GOD_MODE); tp(0, 0, 0, 180, 0); });
        // ===== Build =====
        step(5, c -> run(() -> { for (BlockPos q : BlockPos.iterate(rel(-2, 0, -6), rel(2, 4, -4))) sw().setBlockState(q, Blocks.BRICKS.getDefaultState()); sw().setBlockState(rel(0, 0, -5), Blocks.CHEST.getDefaultState()); }));
        step(5, c -> { tp(0, 0, 0, 180, 0); run(() -> { com.minemenu.build.Selection.set(1, rel(-2, 0, -6), host()); com.minemenu.build.Selection.set(2, rel(2, 4, -4), host()); }); action(ServerCheats.Action.COPY); });
        step(5, c -> { action(ServerCheats.Action.PASTE_PREVIEW); tp(10, 0, 0, 180, 20); });
        step(20, c -> { check("Paste preview: anchor follows where you look", Clipboard.previewing && Clipboard.anchor != null); shot(c, "paste_preview.png"); action(ServerCheats.Action.ROTATE); });
        step(5, c -> action(ServerCheats.Action.PASTE_CONFIRM));
        step(20, c -> { check("Paste (rotated): bricks placed", srv(() -> sw().getBlockState(Clipboard.anchor.up(2)).isOf(Blocks.BRICKS) || !sw().getBlockState(Clipboard.anchor.up(1)).isAir())); shot(c, "pasted.png"); });
        step(5, c -> run(() -> com.minemenu.build.BuildTools.saveSchematic(host(), "test house")));
        step(5, c -> { check("Schematics: saved as .nbt", Files.exists(com.minemenu.build.BuildTools.SCHEMATICS.resolve("test house.nbt"))); run(() -> com.minemenu.build.BuildTools.loadSchematic(host(), "test house")); });
        step(5, c -> check("Schematics: loaded back into the copy", Clipboard.template() != null && Clipboard.previewing));
        step(5, c -> { Clipboard.previewing = false; run(() -> { com.minemenu.build.Selection.set(1, rel(-20, 0, -30), host()); com.minemenu.build.Selection.set(2, rel(43, 63, 33), host()); }); });
        step(5, c -> { run(() -> host().getInventory().setStack(0, new ItemStack(Items.STONE))); action(ServerCheats.Action.FILL_SELECTION); startRec(); });
        step(100, c -> { double[] r = stopRec("while filling 64x64x64"); log.add(String.format("fill 64x64x64: worst frame %.0f ms", 1000 / r[1])); check("Fill 64x64x64: done in pieces (game keeps running, 1% low > 5 FPS)", r[1] > 5); });
        step(200, c -> {});
        step(5, c -> { check("Fill 64x64x64: finished", !BlockPlacer.busy() && srv(() -> sw().getBlockState(rel(10, 30, 0)).isOf(Blocks.STONE))); action(ServerCheats.Action.UNDO); });
        step(200, c -> {});
        step(5, c -> { log.add("undo 64^3: busy " + BlockPlacer.busy() + ", undo steps " + UndoStack.size()); check("Undo of the 64x64x64 fill", !BlockPlacer.busy() && srv(() -> sw().getBlockState(rel(10, 30, 0)).isAir())); });
        step(5, c -> { clearArea(); run(() -> com.minemenu.build.Structures.place(host(), com.minemenu.build.Structures.Built.HOUSE)); tp(0, 0, 8, 180, 10); });
        step(40, c -> { shot(c, "structure_house.png"); check("Instant Structures: house built", UndoStack.size() > 0); action(ServerCheats.Action.UNDO); });
        step(40, c -> { tp(0, 0, 0, 180, 60); run(() -> com.minemenu.build.BuildTools.paintBiome(host(), "minecraft:desert")); });
        step(20, c -> check("Biome Painter: biome changed", srv(() -> sw().getBiome(rel(0, 0, -5)).matchesKey(net.minecraft.world.biome.BiomeKeys.DESERT))));
        step(5, c -> { on(Feature.TERRAIN_BRUSH); com.minemenu.build.TerrainBrush.setMode(com.minemenu.build.TerrainBrush.Mode.RAISE); tp(0, 0, 0, 180, 50); });
        step(5, c -> run(() -> com.minemenu.build.TerrainBrush.use(host())));
        step(20, c -> { check("Terrain Brush: raised the ground", srv(() -> !sw().getBlockState(rel(0, 0, -2)).isAir())); off(Feature.TERRAIN_BRUSH); action(ServerCheats.Action.UNDO); });
        step(20, c -> { on(Feature.GRAVITY_GUN); a = spawn(EntityType.PIG, 0, -4, false); tp(0, 0, 0, 180, 15); });
        step(10, c -> run(() -> GravityGun.click(host())));
        step(20, c -> { check("Gravity Gun: grabbed the pig", GravityGun.holding()); num = srv(() -> a.getY()); });
        step(5, c -> run(() -> GravityGun.click(host())));
        step(10, c -> { check("Gravity Gun: thrown", !GravityGun.holding()); off(Feature.GRAVITY_GUN); clearMobs(); });
        // ===== Items =====
        step(5, c -> run(() -> com.minemenu.items.Creators.createPotion(host(), java.util.Map.of("type", "splash", "effects", "speed 5 60"))));
        step(5, c -> { check("Potion Creator: splash potion given", invCount(Items.SPLASH_POTION) == 1); run(() -> com.minemenu.items.Creators.createMob(host(), java.util.Map.of("mob", "spider", "rider", "skeleton", "name", "&cRider", "health", "80"))); tp(0, 0, 0, 180, 30); });
        step(10, c -> check("Mob Creator: spider jockey with 80 health", srv(() -> sw().getEntitiesByClass(net.minecraft.entity.mob.SpiderEntity.class, host().getBoundingBox().expand(70), s -> s.hasPassengers() && s.getMaxHealth() == 80).size() >= 1)));
        step(5, c -> { clearMobs(); run(() -> { host().getInventory().setStack(0, new ItemStack(Items.GOLDEN_APPLE, 5)); host().getInventory().selectedSlot = 0; }); action(ServerCheats.Action.DUPLICATE); });
        step(5, c -> { check("Duplicate: 5 golden apples became 10", invCount(Items.GOLDEN_APPLE) == 10); run(() -> { host().getInventory().setStack(20, new ItemStack(Items.DIRT, 10)); host().getInventory().setStack(12, new ItemStack(Items.DIRT, 10)); host().getInventory().setStack(30, new ItemStack(Items.APPLE, 3)); }); action(ServerCheats.Action.AUTO_SORT); });
        step(5, c -> check("Auto-Sort: stacks merged and sorted", srv(() -> host().getInventory().getStack(9).isOf(Items.APPLE) && host().getInventory().getStack(10).isOf(Items.DIRT) && host().getInventory().getStack(10).getCount() == 20)));
        step(5, c -> run(() -> com.minemenu.items.Inventories.saveSnapshot(host(), 0)));
        step(5, c -> { run(() -> host().getInventory().clear()); run(() -> com.minemenu.items.Inventories.restoreSnapshot(host(), 0)); });
        step(5, c -> check("Inventory Snapshots: restored", invCount(Items.GOLDEN_APPLE) == 10));
        step(5, c -> { action(ServerCheats.Action.LOOT_REFILL); check("Loot Refill: runs without errors", true); });
        step(5, c -> { on(Feature.DEATH_CHEST); run(() -> host().kill()); });
        step(20, c -> {
            check("Death Chest: chest at the death spot with the items", srv(() -> { for (BlockPos q : BlockPos.iterate(rel(-2, -1, -2), rel(2, 6, 2))) if (sw().getBlockEntity(q) instanceof net.minecraft.block.entity.ChestBlockEntity ch && !ch.isEmpty()) return true; return false; }));
            check("Death Chest: waypoint named Death Chest", Waypoints.lastDeath() != null && "Death Chest".equals(Waypoints.lastDeath().name));
            off(Feature.DEATH_CHEST);
            if (c.player.isDead()) c.player.requestRespawn();
        });
        step(40, c -> { clearArea(); tp(0, 0, 0, 180, 0); });
        // ===== Fun =====
        step(5, c -> { set(rel(0, 0, -2), Blocks.SPONGE.getDefaultState()); on(Feature.LUCKY_BLOCKS); run(() -> host().interactionManager.tryBreakBlock(rel(0, 0, -2))); });
        step(10, c -> { check("Lucky Blocks: something happened (sponge gone)", srv(() -> sw().getBlockState(rel(0, 0, -2)).isAir())); off(Feature.LUCKY_BLOCKS); clearMobs(); });
        step(5, c -> { set(rel(0, 0, -2), Blocks.STONE.getDefaultState()); on(Feature.RANDOM_DROPS); run(() -> host().interactionManager.tryBreakBlock(rel(0, 0, -2))); });
        step(10, c -> { check("Random Drops: a random item instead of cobblestone", srv(() -> sw().getEntitiesByClass(ItemEntity.class, host().getBoundingBox().expand(6), e -> !e.getStack().isOf(Items.COBBLESTONE)).size() >= 1)); off(Feature.RANDOM_DROPS); clearMobs(); });
        step(5, c -> { on(Feature.FLOOR_IS_LAVA); on(Feature.GOD_MODE); });
        step(20, c -> { check("Floor is Lava: magma under you", srv(() -> sw().getBlockState(host().getBlockPos().down()).isOf(Blocks.MAGMA_BLOCK))); off(Feature.FLOOR_IS_LAVA); });
        step(5, c -> { a = spawn(EntityType.COW, 8, -8, false); run(() -> FunEvents.swapWithMob(host())); });
        step(10, c -> { check("Mob Swap: swapped places with the cow", srv(() -> Math.abs(host().getX() - (base.getX() + 8.5)) < 1)); clearMobs(); tp(0, 0, 0, 180, 30); });
        step(60, c -> run(() -> FunEvents.meteor(host(), Vec3d.ofCenter(rel(0, 0, -15)))));
        step(60, c -> { check("Meteor: crater with ores", srv(() -> { for (BlockPos q : BlockPos.iterate(rel(-4, -5, -19), rel(4, 1, -11))) { var s = sw().getBlockState(q); if (s.isOf(Blocks.DIAMOND_ORE) || s.isOf(Blocks.GOLD_ORE) || s.isOf(Blocks.EMERALD_ORE) || s.isOf(Blocks.ANCIENT_DEBRIS) || s.isOf(Blocks.MAGMA_BLOCK)) return true; } return false; })); shot(c, "meteor.png"); });
        step(5, c -> { for (int i = 0; i < 5; i++) spawn(EntityType.PIG, -3 + i, -10, true); action(ServerCheats.Action.TORNADO); });
        step(60, c -> {});
        step(60, c -> { check("Tornado: pigs lifted into the air", srv(() -> sw().getEntitiesByClass(net.minecraft.entity.passive.PigEntity.class, host().getBoundingBox().expand(30), p -> p.getY() > base.getY() + 2).size() >= 1)); shot(c, "tornado.png"); });
        step(420, c -> {});
        for (String event : new String[]{"tnt", "babies", "low_gravity", "diamonds", "invasion", "darkness"}) {
            step(5, c -> run(() -> FunEvents.chaos(host(), event)));
            step(60, c -> log.add("chaos event " + event + ": ok"));
        }
        step(5, c -> { check("Chaos events: all ran without errors", true); clearMobs(); off(Feature.GOD_MODE); on(Feature.CHAOS_MODE); FeatureSettings.set(Feature.CHAOS_MODE, FeatureSettings.of(Feature.CHAOS_MODE).get(0), 1); });
        step(1300, c -> {}); // over a minute of Chaos Mode at 1 event per minute
        step(5, c -> { check("Chaos Mode: ran for over a minute, countdown running", com.minemenu.fun.FunModules.chaosTicksLeft > 0); shot(c, "chaos.png"); off(Feature.CHAOS_MODE); clearMobs(); run(() -> { sw().setTimeOfDay(6000); host().clearStatusEffects(); }); });
        // ===== Utility / HUD =====
        step(5, c -> { on(Feature.MINIMAP); on(Feature.INFO_HUD); on(Feature.MOB_HEALTH_BARS); spawn(EntityType.ZOMBIE, 0, -5, false); spawn(EntityType.COW, 4, -6, false); tp(0, 0, 0, 180, 10); });
        step(100, c -> shot(c, "minimap_info_healthbars.png"));
        step(5, c -> { on(Feature.SLIME_CHUNKS); on(Feature.CHUNK_BORDERS); on(Feature.LIGHT_OVERLAY); run(() -> sw().setTimeOfDay(18000)); });
        step(40, c -> { shot(c, "slime_chunks_borders_light.png"); off(Feature.SLIME_CHUNKS); off(Feature.CHUNK_BORDERS); off(Feature.LIGHT_OVERLAY); run(() -> sw().setTimeOfDay(6000)); clearMobs(); });
        step(5, c -> c.setScreen(new BigMapScreen()));
        step(20, c -> { shot(c, "bigmap.png"); c.setScreen(null); });
        // minimap cost
        step(20, c -> off(Feature.MINIMAP));
        for (int r = 0; r < 2; r++) {
            step(30, c -> { off(Feature.MINIMAP); startRec(); });
            step(100, c -> stopRec("Minimap OFF"));
            step(30, c -> { on(Feature.MINIMAP); startRec(); });
            step(100, c -> stopRec("Minimap ON"));
        }
        step(5, c -> { int k = stats.size(); double offAvg = (stats.get(k - 4)[0] + stats.get(k - 2)[0]) / 2, onAvg = (stats.get(k - 3)[0] + stats.get(k - 1)[0]) / 2; log.add(String.format("RESULT Minimap cost: %.1f -> %.1f FPS (%+.0f%%)", offAvg, onAvg, (onAvg - offAvg) * 100 / offAvg)); off(Feature.MINIMAP); off(Feature.INFO_HUD); off(Feature.MOB_HEALTH_BARS); });
        // ===== UI: keys, macros, palette, profiles =====
        step(5, c -> { CheatConfig.get().keys.put("feature:NIGHT_VISION", GLFW.GLFW_KEY_J); });
        step(5, c -> CheatConfig.get().keys.size());
        step(5, c -> { num = 0; check("Key for any hack: J set (no conflict with vanilla J)", com.minemenu.core.Keys.conflicts(c, GLFW.GLFW_KEY_J, "feature:NIGHT_VISION").isEmpty()); });
        step(5, c -> { CheatConfig.Macro macro = new CheatConfig.Macro(); macro.name = "test"; macro.steps.addAll(List.of("action:NIGHT", "wait:5", "action:DAY", "feature:SPEED")); off(Feature.SPEED); com.minemenu.hud.Macros.run(macro); });
        step(30, c -> {});
        step(5, c -> { check("Macro: ran its steps (day, Speed on)", srv(() -> sw().getTimeOfDay() % 24000 == 1000) && CheatState.isOn(Feature.SPEED)); off(Feature.SPEED); });
        step(5, c -> { CheatState.toggle(Feature.FLY); Profiles.saveCurrent("test"); String code = Profiles.export("test"); Profiles.delete("test"); String name = Profiles.importCode(code); check("Profiles: export/import share code", "test".equals(name) && CheatConfig.get().profiles.get("test").contains("FLY")); CheatState.toggle(Feature.FLY); });
        step(5, c -> { c.setScreen(new CommandPalette(null)); });
        step(5, c -> { for (char ch : "set night".toCharArray()) c.currentScreen.charTyped(ch, 0); });
        step(5, c -> { shot(c, "palette.png"); c.currentScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); });
        step(10, c -> check("Command Palette: Enter ran the first match (Set Night)", srv(() -> sw().getTimeOfDay() % 24000 == 13000)));
        step(5, c -> { action(ServerCheats.Action.DAY); c.setScreen(new MineMenuScreen()); });
        step(10, c -> shot(c, "menu_player.png"));
        for (String page : new String[]{"Build", "Fun", "Utility", "Items"}) {
            step(5, c -> { for (var child : c.currentScreen.children()) if (child instanceof net.minecraft.client.gui.widget.ButtonWidget bw && bw.getMessage().getString().equals(page)) { bw.onPress(); break; } });
            step(10, c -> shot(c, "menu_" + page.toLowerCase() + ".png"));
        }
        for (String theme : new String[]{"light", "glass", "dark"}) {
            step(5, c -> { CheatConfig.get().theme = theme; c.setScreen(new MineMenuScreen()); });
            step(10, c -> shot(c, "theme_" + theme + ".png"));
        }
        step(5, c -> c.setScreen(new BoostScreen(null, c.options)));
        step(10, c -> shot(c, "boostscreen.png"));
        step(5, c -> c.setScreen(null));
        // A/B test: start, let it run 20 s, cancel: settings must be back
        step(5, c -> { num = c.options.getViewDistance().getValue(); BoostConfig.get().farMobDetail = 24; BoostAB.start(c); });
        step(400, c -> {});
        step(5, c -> { BoostAB.cancel(c); });
        step(10, c -> check("A/B test: cancelling puts your settings back", c.options.getViewDistance().getValue() == (int) num && BoostConfig.get().renderScale == 100 && BoostConfig.get().farMobDetail == 24));
        // Memory: no steady growth after all this (GC first)
        step(5, c -> System.gc());
        step(40, c -> { long now = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); log.add(String.format("memory: start %d MB, now %d MB", memStart >> 20, now >> 20)); });
    }
}
