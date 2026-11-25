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

/** With Iris (+ Sodium, Mod Menu): Render Scale and Impostors must lock themselves, menus and hacks must work. */
class IrisSuite extends Harness {
    IrisSuite() {
        super("iris");
    }

    @Override
    protected void build() {
        buildIris();
    }

    private void buildIris() {
        step(200, this::setupArena);
        step(5, c -> {
            log.add("mods: " + com.mineboost.Compat.detected() + ", turned off: " + com.mineboost.Compat.turnedOff());
            log.add("render scale unavailable because: " + com.mineboost.renderscale.RenderScale.unavailableReason(c));
            BoostConfig.get().renderScale = 50;
        });
        step(40, c -> { check("Iris: Render Scale stays off even when set to 50%", !com.mineboost.renderscale.RenderScale.active(c)); shot(c, "scale50_locked.png"); BoostConfig.get().renderScale = 100; });
        step(5, c -> {
            boolean modmenu = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu");
            log.add("modmenu loaded " + modmenu);
            if (modmenu) {
                var screen = new com.minemeboost.ModMenuIntegration().getModConfigScreenFactory().create(null);
                check("Mod Menu: config button opens the MineMeBoost menu", screen != null);
                c.setScreen(screen);
            }
        });
        step(10, c -> { shot(c, "modmenu_config.png"); c.setScreen(new BoostScreen(null, c.options)); });
        step(10, c -> { shot(c, "boost_iris.png"); c.setScreen(new MineMenuScreen()); });
        step(10, c -> { if (c.currentScreen instanceof com.minemenu.ui.WhatsNewScreen) c.currentScreen.close(); });
        step(10, c -> { shot(c, "menu_iris.png"); c.setScreen(null); on(Feature.ORE_VISION); on(Feature.FREECAM); on(Feature.MINIMAP); });
        step(60, c -> { shot(c, "hacks_iris.png"); check("Iris: hacks run without errors (game alive)", c.player != null); for (Feature f : Feature.values()) off(f); });
        step(5, c -> {});
    }
}
