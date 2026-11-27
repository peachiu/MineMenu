package com.minexray.clienttest;

import com.minexray.MineXrayClient;
import com.minexray.PresetManager;
import com.minexray.XrayConfig;
import com.minexray.XrayHost;
import com.minexray.XrayKeys;
import com.minexray.XrayMode;
import com.minexray.mode.ClassicXray;
import com.minexray.render.EspRenderer;
import com.minexray.render.LabelRenderer;
import com.minexray.scan.ScanCache;
import com.minexray.scan.VeinMerger;
import com.minexray.screen.XrayScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** MineXray on its own (no MineMenu, no MineBoost): makes a world and checks that the mod and its tab work. */
public class StandaloneTest implements ClientModInitializer {
    private record Step(int waitAfter, Consumer<MinecraftClient> action) {
    }

    private final List<Step> steps = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private int stepIndex = -2;
    private int wait;
    private IntegratedServer server;
    private BlockPos base;

    @Override
    public void onInitializeClient() {
        build();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(15 * 60_000L);
            } catch (InterruptedException e) {
                return;
            }
            log.add("ERROR watchdog: not finished after 15 minutes (step " + stepIndex + ")");
            write();
            Runtime.getRuntime().halt(2);
        }, "minexray test watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void step(int wait, Consumer<MinecraftClient> action) {
        steps.add(new Step(wait, action));
    }

    private void check(String name, boolean ok) {
        log.add((ok ? "PASS " : "FAIL ") + name);
    }

    private void run(Runnable r) {
        try {
            server.submit(r).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ServerPlayerEntity host() {
        return server.getPlayerManager().getPlayerList().get(0);
    }

    private BlockPos rel(int x, int y, int z) {
        return base.add(x, y, z);
    }

    private void shot(MinecraftClient c, String name) {
        ScreenshotRecorder.saveScreenshot(c.runDirectory, "standalone_" + name, c.getFramebuffer(), t -> {
        });
    }

    private VeinMerger.Vein find(net.minecraft.block.Block block) {
        for (VeinMerger.Vein vein : VeinMerger.veins()) {
            if (vein.block() == block) {
                return vein;
            }
        }
        return null;
    }

    private void build() {
        step(5, c -> {
            c.getWindow().setWindowedSize(1366, 768);
            c.options.getGuiScale().setValue(2);
            check("MineXray runs on its own (not inside MineMeBoostXray)", !XrayHost.embedded());
            check("Config in config/minexray/", XrayConfig.file().toString().replace('\\', '/').endsWith("config/minexray/minexray.json"));
            check("Open key: Right Shift on its own", XrayKeys.openTab.getDefaultKey().getCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
            check("9 built-in presets", PresetManager.all().stream().filter(p -> p.builtIn).count() >= 9);
            check("Allowed in singleplayer", MineXrayClient.allowed());
            XrayConfig.get().adaptiveRange = false;
            run(() -> {
                ServerPlayerEntity p = host();
                base = new BlockPos((int) p.getX(), 150, (int) p.getZ());
                ServerWorld w = p.getServerWorld();
                server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                w.setTimeOfDay(6000);
                for (BlockPos q : BlockPos.iterate(rel(-12, -16, -26), rel(12, -1, 4))) w.setBlockState(q, Blocks.STONE.getDefaultState());
                for (BlockPos q : BlockPos.iterate(rel(-12, 0, -26), rel(12, 12, 4))) w.setBlockState(q, Blocks.AIR.getDefaultState());
                for (BlockPos q : BlockPos.iterate(rel(0, -9, -12), rel(1, -8, -11))) w.setBlockState(q, Blocks.DIAMOND_ORE.getDefaultState());
                w.setBlockState(rel(-5, -5, -8), Blocks.CHEST.getDefaultState());
                p.changeGameMode(GameMode.CREATIVE);
                p.getAbilities().flying = true;
                p.requestTeleport(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
                p.setYaw(180);
                p.setPitch(30);
            });
        });
        step(40, c -> {
            c.player.setYaw(180);
            c.player.setPitch(30);
            XrayKeys.toggle(c, XrayMode.ORE_ESP);
        });
        step(60, c -> {});
        step(5, c -> {
            VeinMerger.Vein diamond = find(Blocks.DIAMOND_ORE);
            check("Ore ESP: scans in the background", ScanCache.size() > 0);
            check("Ore ESP: the diamond cube is one vein of 8", diamond != null && diamond.count() == 8);
            check("Ore ESP: drawn (" + EspRenderer.drawCalls + " draw calls)", EspRenderer.drawCalls > 0);
            check("Ore ESP: labels on screen", LabelRenderer.drawn > 0);
            shot(c, "ore_esp.png");
            run(() -> host().getServerWorld().setBlockState(rel(1, -8, -11), Blocks.STONE.getDefaultState()));
        });
        step(20, c -> {
            VeinMerger.Vein diamond = find(Blocks.DIAMOND_ORE);
            check("Ore ESP: a broken block updates the vein (8 -> 7)", diamond != null && diamond.count() == 7);
            XrayKeys.toggle(c, XrayMode.BLOCK_ESP);
        });
        step(40, c -> check("Block ESP: the chest is found", find(Blocks.CHEST) != null));
        step(5, c -> XrayKeys.toggle(c, XrayMode.CLASSIC));
        step(160, c -> {
            check("Classic X-Ray: on (own full-bright, chunk culling off)", ClassicXray.active() && ClassicXray.fullBright() && !c.chunkCullingEnabled);
            check("Classic X-Ray: stone hidden, diamond shown", ClassicXray.skip(Blocks.STONE.getDefaultState()) && !ClassicXray.skip(Blocks.DIAMOND_ORE.getDefaultState()));
            shot(c, "classic.png");
            XrayKeys.toggle(c, XrayMode.CLASSIC);
        });
        step(100, c -> check("Classic X-Ray: off again, culling back", !ClassicXray.active() && c.chunkCullingEnabled));
        for (XrayScreen.Page page : XrayScreen.Page.values()) {
            step(10, c -> c.setScreen(new XrayScreen(null, page, false)));
            step(10, c -> {
                check("Tab: " + page + " page opens", c.currentScreen instanceof XrayScreen s && s.page() == page && s.content() != null);
                shot(c, "page_" + page.name().toLowerCase() + ".png");
            });
        }
        step(10, c -> {
            c.setScreen(new XrayScreen(null, XrayScreen.Page.SETTINGS, false));
        });
        step(10, c -> {
            boolean costGrey = false;
            for (var child : c.currentScreen.children()) {
                if (child instanceof ButtonWidget b && b.getMessage().getString().contains("A/B")) {
                    costGrey = !b.active;
                }
            }
            check("Without MineBoost the cost test is greyed out (no crash)", costGrey);
            check("Without MineMenu there are no waypoints (button greyed out)", !com.minexray.XrayActions.waypointsAvailable());
            c.setScreen(null);
            for (XrayMode mode : XrayMode.values()) {
                XrayConfig.get().set(mode, false);
            }
        });
        step(40, c -> check("All modes off: nothing kept", ScanCache.size() == 0 && EspRenderer.drawCalls == 0));
    }

    private void tick(MinecraftClient client) {
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            if (stepIndex == -2) {
                if (client.currentScreen instanceof net.minecraft.client.gui.screen.AccessibilityOnboardingScreen onboarding) {
                    onboarding.close();
                    return;
                }
                if (client.currentScreen instanceof TitleScreen) {
                    client.options.getMaxFps().setValue(260);
                    client.createIntegratedServerLoader().createAndStart("xraytest",
                            new LevelInfo("xraytest", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE),
                            GeneratorOptions.createRandom(), WorldPresets::createDemoOptions, client.currentScreen);
                    stepIndex = -1;
                }
                return;
            }
            if (stepIndex == -1) {
                if (client.player != null && client.world != null && client.currentScreen == null) {
                    server = client.getServer();
                    stepIndex = 0;
                    wait = 100;
                }
                return;
            }
            if (stepIndex < steps.size()) {
                Step s = steps.get(stepIndex++);
                s.action().accept(client);
                wait = s.waitAfter();
            } else if (stepIndex == steps.size()) {
                stepIndex++;
                write();
                client.scheduleStop();
            }
        } catch (Throwable t) {
            log.add("ERROR at step " + (stepIndex - 1) + ": " + t);
            for (StackTraceElement e : t.getStackTrace()) {
                log.add("  at " + e);
            }
            write();
            client.scheduleStop();
            stepIndex = 99999;
        }
    }

    private void write() {
        try {
            Path dir = Path.of("clienttest");
            Files.createDirectories(dir);
            Files.write(dir.resolve("results-standalone.txt"), log);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
