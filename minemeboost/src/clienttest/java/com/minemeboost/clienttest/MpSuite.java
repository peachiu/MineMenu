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

/** Multiplayer: joins a dedicated server on localhost:25599 and checks that cheats stay off. */
class MpSuite extends Harness {
    MpSuite() {
        super("mp");
    }

    @Override
    protected void build() {
        buildMultiplayer();
    }

    /** Tells the test script which player to op (it watches this file and types "op NAME" in the server console). */
    private static void writeOpRequest(String name) {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("clienttest"));
            java.nio.file.Files.writeString(java.nio.file.Path.of("clienttest", "op-request.txt"), name);
        } catch (Exception ignored) {
        }
    }

    private void buildMultiplayer() {
        step(100, c -> {});
        step(60, c -> {
            log.add("connected to a server: " + (c.getCurrentServerEntry() != null));
            check("Multiplayer: not seen as singleplayer", !MineMenuClient.isSingleplayer(c));
            on(Feature.FLY);
            on(Feature.ORE_VISION);
            on(Feature.CHEST_ESP);
            on(Feature.MINIMAP);
            on(Feature.INFO_HUD);
            on(Feature.FREECAM);
            on(Feature.FULL_BRIGHT);
            on(Feature.MOB_HEALTH_BARS);
            for (com.minexray.XrayMode mode : com.minexray.XrayMode.values()) com.minexray.XrayConfig.get().set(mode, true);
            com.minexray.XrayKeys.toggle(c, com.minexray.XrayMode.ORE_ESP);
        });
        step(60, c -> {
            check("Multiplayer: MineXray not allowed", !com.minexray.MineXrayClient.allowed());
            check("Multiplayer: MineXray modes inactive even when switched on", java.util.Arrays.stream(com.minexray.XrayMode.values()).noneMatch(com.minexray.MineXrayClient::active));
            check("Multiplayer: MineXray scans nothing", com.minexray.scan.ScanCache.size() == 0 && com.minexray.scan.SectionScanner.needs() == null);
            check("Multiplayer: MineXray draws nothing", com.minexray.render.EspRenderer.drawCalls == 0 && com.minexray.render.OverlayRenderer.drawnEntities == 0);
            check("Multiplayer: Classic X-Ray not active", !com.minexray.mode.ClassicXray.active() && c.chunkCullingEnabled);
            check("Multiplayer: Fly not granted", !c.player.getAbilities().allowFlying);
            check("Multiplayer: Freecam not active", !Freecam.active() && c.getCameraEntity() == c.player);
            check("Multiplayer (5.0): Full Bright works on servers (only your screen)", PlayerModules.fullBrightOn);
            check("Multiplayer: Ore Vision doesn't scan", SectionScanner.cachedSections() == 0);
            check("Multiplayer: Chest ESP doesn't scan", EspModules.chestCount() == 0);
            check("Multiplayer (5.0): health bars work on servers (only your screen)", com.minemenu.hud.HealthBars.on);
            check("Multiplayer: not OP yet, so no command versions", !com.minemenu.op.OpCommands.active(c));
            com.minemenu.Waypoints.setHereClient(c, 0, "MP test");
            shot(c, "mp_hud.png");
            c.setScreen(new MineMenuScreen());
        });
        step(10, c -> { shot(c, "mp_menu.png"); c.setScreen(new com.minexray.screen.XrayScreen(null, com.minexray.screen.XrayScreen.Page.OVERVIEW, false)); });
        step(10, c -> { shot(c, "mp_xray_tab.png"); c.setScreen(null);
            for (com.minexray.XrayMode mode : com.minexray.XrayMode.values()) com.minexray.XrayConfig.get().set(mode, mode == com.minexray.XrayMode.ORE_ESP
                    || mode == com.minexray.XrayMode.BLOCK_ESP || mode == com.minexray.XrayMode.ENTITY_ESP);
            com.minexray.XrayConfig.get().adaptiveRange = false;
            com.minexray.XrayConfig.get().allowMultiplayer = true; // 4.0: switched on by the player
        });
        step(200, c -> {
            check("Multiplayer (allowed): MineXray runs on the server", com.minexray.MineXrayClient.allowed() && com.minexray.MineXrayClient.active(com.minexray.XrayMode.ORE_ESP));
            check("Multiplayer (allowed): sections scanned on the client (" + com.minexray.scan.ScanCache.size() + ")", com.minexray.scan.ScanCache.size() > 0);
            check("Multiplayer (allowed): world-changing actions stay off (teleport, mine, waypoints)", !com.minexray.MineXrayClient.ownWorld());
            check("Multiplayer (allowed): MineMenu cheats still off (no flying)", !c.player.getAbilities().allowFlying);
            shot(c, "mp_xray_allowed.png");
            c.setScreen(new com.minexray.screen.XrayScreen(null, com.minexray.screen.XrayScreen.Page.SCANNER, false));
        });
        step(10, c -> {
            boolean teleportOff = false;
            for (var child : c.currentScreen.children()) {
                if (child instanceof net.minecraft.client.gui.widget.ButtonWidget b && b.getMessage().getString().equals("TP")) teleportOff = !b.active;
            }
            log.add("INFO scanner page opened on the server (teleport buttons greyed out: " + teleportOff + ")");
            shot(c, "mp_xray_scanner.png");
            c.setScreen(null);
            com.minexray.XrayConfig.get().allowMultiplayer = false;
        });
        step(40, c -> {
            check("Multiplayer: switching it off again stops it and frees everything", !com.minexray.MineXrayClient.allowed() && com.minexray.scan.ScanCache.size() == 0);
            for (com.minexray.XrayMode mode : com.minexray.XrayMode.values()) com.minexray.XrayConfig.get().set(mode, false);
            c.setScreen(new BoostScreen(null, c.options));
            check("Multiplayer (5.0): waypoints saved on your computer for this server", com.minemenu.Waypoints.get(0) != null
                    && java.nio.file.Files.exists(com.minemenu.core.CheatConfig.DIR.resolve("server-waypoints/localhost_25599.json")));
            log.add("OPME " + c.player.getGameProfile().getName()); // the test script ops this player from the server console
            writeOpRequest(c.player.getGameProfile().getName());
        });
        for (int i = 0; i < 60; i++) {
            step(20, c -> { if (c.player.hasPermissionLevel(2)) skipWait(); });
        }
        step(5, c -> {
            c.setScreen(null);
            check("Multiplayer OP (5.0): commands available once you're OP", com.minemenu.op.OpCommands.active(c));
            if (CheatState.isOn(Feature.GOD_MODE)) CheatState.toggle(Feature.GOD_MODE);
            if (CheatState.isOn(Feature.FLY)) CheatState.toggle(Feature.FLY);
            com.minemenu.ui.MenuEntries.run(c, null, com.minemenu.ui.MenuEntries.hack(Feature.GOD_MODE));
            com.minemenu.ui.MenuEntries.run(c, null, com.minemenu.ui.MenuEntries.hack(Feature.FLY));
            com.minemenu.ui.MenuEntries.run(c, null, com.minemenu.ui.MenuEntries.action(com.minemenu.ServerCheats.Action.NIGHT));
            com.minemenu.op.OpCommands.giveItem(c, net.minecraft.util.Identifier.ofVanilla("diamond"), 5);
        });
        step(100, c -> {
            check("Multiplayer OP: God Mode by command (resistance effect)", c.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE));
            check("Multiplayer OP: Fly by command (creative)", c.player.getAbilities().allowFlying);
            long time = c.world.getTimeOfDay() % 24000;
            check("Multiplayer OP: Night by command (time " + time + ")", time >= 12000);
            int diamonds = 0;
            for (int i = 0; i < c.player.getInventory().size(); i++) if (c.player.getInventory().getStack(i).isOf(net.minecraft.item.Items.DIAMOND)) diamonds += c.player.getInventory().getStack(i).getCount();
            check("Multiplayer OP: Give Item by command (" + diamonds + " diamonds)", diamonds >= 5);
            shot(c, "mp_op.png");
            com.minemenu.ui.MenuEntries.run(c, null, com.minemenu.ui.MenuEntries.hack(Feature.GOD_MODE));
            com.minemenu.ui.MenuEntries.run(c, null, com.minemenu.ui.MenuEntries.hack(Feature.FLY));
        });
        step(40, c -> {
            check("Multiplayer OP: switching them off undoes them", !c.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE)
                    && !c.player.getAbilities().allowFlying);
            c.setScreen(new com.minemenu.op.ServerAdminScreen(null));
        });
        step(20, c -> {
            check("Multiplayer OP (6.0): Server Admin works with OP", com.minemenu.op.Cmd.available(c));
            shot(c, "mp_admin.png");
            com.minemenu.op.Cmd.run(c, "gamerule doInsomnia false"); // the test script checks the server log for it
            c.setScreen(new BoostScreen(null, c.options));
        });
        step(10, c -> { shot(c, "mp_boost.png"); c.setScreen(null); BoostConfig.get().renderScale = 75; });
        step(20, c -> { shot(c, "mp_renderscale75.png"); check("Multiplayer: Render Scale works", com.mineboost.renderscale.RenderScale.active(c)); BoostConfig.get().renderScale = 100; });
        step(5, c -> { for (Feature f : Feature.values()) off(f); });
    }

}
