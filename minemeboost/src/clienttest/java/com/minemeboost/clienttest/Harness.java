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

/**
 * The in-game test runner (src/clienttest, never shipped). A suite is a list of steps; {@code step(waitAfter, action)}
 * runs the action, then waits that many ticks. Results go to clienttest/results-SUITE.txt (PASS / FAIL / MEASURE lines),
 * screenshots to screenshots/. See ClientTestMod and the runClientTests Gradle task.
 */
public abstract class Harness {
    private record Step(int waitAfter, Consumer<MinecraftClient> action) {}
    private final List<Step> steps = new ArrayList<>();
    private int stepIndex = -2, wait;
    protected final List<String> log = new ArrayList<>();
    protected IntegratedServer server;
    protected BlockPos base;
    protected final String label;
    protected final boolean mp;
    private boolean recording;
    private long lastFrame;
    private final long[] times = new long[200_000];
    private int n;
    protected final List<double[]> stats = new ArrayList<>();
    protected double num, num2;
    protected int count;
    protected Entity a, b;
    protected long memStart;

    protected Harness(String suite) {
        this.label = suite;
        this.mp = suite.equals("mp");
    }

    /** Adds the suite's steps. */
    protected abstract void build();

    /** Called every frame from the HUD callback, for FPS measurements. */
    void onFrame() {
        long now = System.nanoTime();
        if (recording && lastFrame != 0 && n < times.length) times[n++] = now - lastFrame;
        lastFrame = now;
    }

    protected void pause(int ticks) { step(ticks, c -> {}); }
    protected void check(String name, boolean ok) { log.add((ok ? "PASS " : "FAIL ") + name); }
    protected double maxY = Double.NaN;
    protected boolean watchClimb;

    private void writeResults() {
        try {
            Path dir = Path.of("clienttest");
            Files.createDirectories(dir);
            Files.write(dir.resolve("results-" + label + ".txt"), log);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected <T> T srv(Supplier<T> s) { try { return server.submit(s).get(); } catch (Exception e) { throw new RuntimeException(e); } }
    protected void run(Runnable r) { srv(() -> { r.run(); return null; }); }
    protected ServerPlayerEntity host() { return ServerCheats.host(server); }
    protected ServerWorld sw() { return host().getServerWorld(); }
    protected void shot(MinecraftClient c, String name) { ScreenshotRecorder.saveScreenshot(c.runDirectory, label + "_" + name, c.getFramebuffer(), t -> {}); }
    protected void step(int w, Consumer<MinecraftClient> a) { steps.add(new Step(w, a)); }
    protected void tp(double dx, double dy, double dz, float yaw, float pitch) {
        run(() -> host().networkHandler.requestTeleport(base.getX() + 0.5 + dx, base.getY() + dy, base.getZ() + 0.5 + dz, yaw, pitch));
    }
    protected BlockPos rel(int x, int y, int z) { return base.add(x, y, z); }
    protected void set(BlockPos p, net.minecraft.block.BlockState s) { run(() -> sw().setBlockState(p, s)); }
    protected void on(Feature f) { if (!CheatState.isOn(f)) CheatState.toggle(f); }
    protected void off(Feature f) { if (CheatState.isOn(f)) CheatState.toggle(f); }
    protected void action(ServerCheats.Action action) { run(() -> ServerCheats.run(server, action)); }
    protected void clearMobs() { run(() -> sw().getOtherEntities(host(), host().getBoundingBox().expand(120), e -> !(e instanceof PlayerEntity)).forEach(Entity::discard)); }
    protected void clearArea() { run(() -> { for (BlockPos q : BlockPos.iterate(rel(-30, 0, -40), rel(30, 20, 30))) sw().setBlockState(q, Blocks.AIR.getDefaultState()); }); }
    protected <T extends MobEntity> T spawn(EntityType<T> type, double dx, double dz, boolean ai) {
        return srv(() -> {
            T mob = type.create(sw());
            mob.refreshPositionAndAngles(base.getX() + 0.5 + dx, base.getY(), base.getZ() + 0.5 + dz, 0, 0);
            mob.setAiDisabled(!ai);
            mob.setPersistent();
            sw().spawnEntity(mob);
            return mob;
        });
    }
    protected float health(Entity e) { return srv(() -> e instanceof LivingEntity l && l.isAlive() ? l.getHealth() : 0f); }
    protected int invCount(net.minecraft.item.Item item) { return srv(() -> { int t = 0; var inv = host().getInventory(); for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(item)) t += inv.getStack(i).getCount(); return t; }); }
    protected void startRec() { n = 0; lastFrame = 0; recording = true; }
    protected double[] stopRec(String name) {
        recording = false;
        long[] s = Arrays.copyOf(times, n);
        long total = 0;
        for (long t : s) total += t;
        Arrays.sort(s);
        int slow = Math.max(1, n / 100);
        long slowTotal = 0;
        for (int i = n - slow; i < n; i++) slowTotal += s[i];
        double[] r = {n * 1e9 / total, slow * 1e9 / slowTotal};
        log.add(String.format("MEASURE %-40s avg %6.1f FPS | 1%% low %6.1f", name, r[0], r[1]));
        stats.add(r);
        return r;
    }
    protected void press(net.minecraft.client.option.KeyBinding binding) {
        KeyBinding2.press(binding);
    }

    /** KeyBinding.onKeyPressed(key) increments the "pressed" counter, like a real key press. */
    private static final class KeyBinding2 {
        static void press(net.minecraft.client.option.KeyBinding binding) {
            net.minecraft.client.option.KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey()));
        }
    }

    protected void setupArena(MinecraftClient c) {
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
        tp(0, 0, 0, 180, 5);
    }




    void tick(MinecraftClient client) {
        if (client.player != null && !Double.isNaN(maxY)) maxY = Math.max(maxY, client.player.getY());
        if (watchClimb && client.player != null) log.add(String.format("  climb: y %.2f z %.2f hc=%s climbing=%s morph=%s", client.player.getY(), client.player.getZ(), client.player.horizontalCollision, client.player.isClimbing(), Morph.climbs()));
        if (wait > 0) { wait--; return; }
        try {
            if (stepIndex == -2) {
                if (client.currentScreen instanceof com.minemenu.ui.WhatsNewScreen) {
                    client.setScreen(null);
                }
                if (client.currentScreen instanceof TitleScreen) {
                    client.options.getMaxFps().setValue(260);
                    client.options.getEnableVsync().setValue(false);
                    if (mp) {
                        net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse("localhost:25599"),
                                new ServerInfo("test", "localhost:25599", ServerInfo.ServerType.OTHER), false, null);
                    } else {
                        client.createIntegratedServerLoader().createAndStart("clienttest",
                                new LevelInfo("clienttest", GameMode.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), DataConfiguration.SAFE_MODE),
                                GeneratorOptions.createRandom(), WorldPresets::createDemoOptions, client.currentScreen);
                    }
                    stepIndex = -1;
                }
                return;
            }
            if (stepIndex == -1) {
                if (client.player != null && client.world != null && client.currentScreen == null) { server = client.getServer(); stepIndex = 0; wait = 100; }
                return;
            }
            if (stepIndex < steps.size()) {
                Step s = steps.get(stepIndex++);
                s.action().accept(client);
                wait = s.waitAfter();
            } else if (stepIndex == steps.size()) {
                stepIndex++;
                writeResults();
                client.scheduleStop();
            }
        } catch (Throwable t) {
            log.add("ERROR at step " + (stepIndex - 1) + ": " + t);
            for (StackTraceElement e : t.getStackTrace()) log.add("  at " + e);
            try { writeResults(); } catch (Exception ignored) { }
            client.scheduleStop();
            stepIndex = 99999;
        }
    }
}
