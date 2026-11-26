package com.minemeboost.clienttest;

import com.minemenu.CheatState;
import com.minemenu.Feature;
import com.minemenu.Waypoints;
import com.minexray.MineXrayClient;
import com.minexray.PresetManager;
import com.minexray.XrayActions;
import com.minexray.XrayConfig;
import com.minexray.XrayKeys;
import com.minexray.XrayMode;
import com.minexray.mode.ClassicXray;
import com.minexray.mode.LavaWarning;
import com.minexray.render.EspRenderer;
import com.minexray.render.OverlayRenderer;
import com.minexray.scan.MeshData;
import com.minexray.scan.ScanCache;
import com.minexray.scan.ScanJob;
import com.minexray.scan.SectionData;
import com.minexray.scan.VeinMerger;
import com.minexray.screen.XrayScreen;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;

import java.util.Set;

/** 3.6: MineXray (every mode, the editor, presets, scanner, migration, Safe Mode). */
abstract class XrayTests extends WorldTests {
    private VeinMerger.Vein diamond;
    private int diamondsBefore;
    private boolean cullingBefore;
    private BlockPos lavaUnder;
    /** Where the diamond cube goes: inside one section, so its outline can be counted. */
    private int dx;
    private int dz;

    XrayTests(String suite) {
        super(suite);
    }

    private VeinMerger.Vein find(net.minecraft.block.Block block) {
        for (VeinMerger.Vein vein : VeinMerger.veins()) {
            if (vein.block() == block) {
                return vein;
            }
        }
        return null;
    }

    private VeinMerger.Vein findIn(java.util.List<VeinMerger.Vein> list, net.minecraft.block.Block block) {
        for (VeinMerger.Vein vein : list) {
            if (vein.block() == block) {
                return vein;
            }
        }
        return null;
    }

    /** Share of the lower-middle of the screen that looks like stone (grey, mid brightness). */
    static double stoneShare(net.minecraft.client.MinecraftClient c) {
        try (net.minecraft.client.texture.NativeImage image = net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            int w = image.getWidth(), h = image.getHeight(), grey = 0, n = 0;
            for (int y = h * 3 / 5; y < h * 4 / 5; y += 2) {
                for (int x = w / 4; x < w * 3 / 4; x += 2) {
                    int p = image.getColor(x, y);
                    int r = p & 0xFF, g = (p >> 8) & 0xFF, b = (p >> 16) & 0xFF;
                    if (Math.abs(r - g) < 14 && Math.abs(g - b) < 14 && r > 70 && r < 170) grey++;
                    n++;
                }
            }
            return grey / (double) n;
        }
    }

    private void allModesOff() {
        for (XrayMode mode : XrayMode.values()) {
            XrayConfig.get().set(mode, false);
        }
    }

    protected void build36() {
        // ----- Setup: a block of stone under the platform with ores, lava, a chest, a spawner and a cave -----
        step(5, c -> { if (c.player.isDead()) c.player.requestRespawn(); });
        step(40, c -> {
            c.setScreen(null);
            c.getWindow().setWindowedSize(1366, 768);
            c.options.getGuiScale().setValue(2);
            c.options.language = "en_us";
            on(Feature.GOD_MODE);
            off(Feature.FREECAM);
            allModesOff();
            XrayConfig.get().adaptiveRange = false; // software rendering is slow: keep the radius fixed for the checks
            PresetManager.apply("All Ores", false);
            clearMobs();
            clearArea();
            dx = ((base.getX() >> 4) << 4) + 6 - base.getX();
            dz = (((base.getZ() - 12) >> 4) << 4) + 6 - base.getZ();
            run(() -> {
                ServerWorld w = sw();
                for (BlockPos q : BlockPos.iterate(rel(-12, -16, -26), rel(12, -1, 4))) w.setBlockState(q, Blocks.STONE.getDefaultState());
                for (BlockPos q : BlockPos.iterate(rel(dx, -9, dz), rel(dx + 1, -8, dz + 1))) w.setBlockState(q, Blocks.DIAMOND_ORE.getDefaultState());
                w.setBlockState(rel(dx + 16, -6, dz), Blocks.GOLD_ORE.getDefaultState()); // in the next section
                w.setBlockState(rel(-5, -5, -8), Blocks.CHEST.getDefaultState());
                w.setBlockState(rel(5, -10, -16), Blocks.SPAWNER.getDefaultState());
                for (BlockPos q : BlockPos.iterate(rel(-5, -9, -14), rel(-4, -9, -13))) w.setBlockState(q, Blocks.LAVA.getDefaultState());
                for (BlockPos q : BlockPos.iterate(rel(-9, -14, -22), rel(-3, -12, -18))) w.setBlockState(q, Blocks.CAVE_AIR.getDefaultState());
                host().setHealth(20);
            });
            tp(0, 0, 0, 180, 30);
        });
        // ----- Base: config file, keys, Command Palette, migration of the old toggles -----
        step(5, c -> {
            check("MineXray: config file written (config/minemeboost/minexray.json)", java.nio.file.Files.exists(XrayConfig.FILE));
            check("MineXray: 9 built-in presets", PresetManager.all().stream().filter(p -> p.builtIn).count() >= 9);
            check("MineXray: Classic X-Ray key X, Ore ESP key G", XrayKeys.of(XrayMode.CLASSIC).getDefaultKey().getCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_X
                    && XrayKeys.of(XrayMode.ORE_ESP).getDefaultKey().getCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_G);
            check("MineXray: Command Palette knows \"xray diamonds\"", com.minemenu.ui.MenuEntries.all().stream()
                    .anyMatch(e -> e.matches("xray diamonds") && e.id().startsWith("xray:preset:")));
            check("MineXray: allowed in singleplayer", MineXrayClient.allowed());
            on(Feature.ORE_VISION); // the old MineMenu toggle
        });
        step(5, c -> {
            check("Migration: MineMenu's Ore Vision switches on MineXray's Ore ESP", XrayConfig.get().oreEsp);
            XrayKeys.toggle(c, XrayMode.ORE_ESP); // off with MineXray...
        });
        step(5, c -> check("Migration: switching Ore ESP off in MineXray switches Ore Vision off too", !CheatState.isOn(Feature.ORE_VISION) && !XrayConfig.get().oreEsp));
        // ----- Ore ESP -----
        step(80, c -> press(XrayKeys.of(XrayMode.ORE_ESP)));
        step(5, c -> {
            check("Ore ESP: on with its key", XrayConfig.get().oreEsp);
            check("Ore ESP: sections scanned in the background", ScanCache.size() > 0);
            diamond = find(Blocks.DIAMOND_ORE);
            check("Ore ESP: the 2x2x2 diamond cube is one vein of 8 (fused)", diamond != null && diamond.count() == 8);
            check("Ore ESP: gold found too", find(Blocks.GOLD_ORE) != null);
            check("Ore ESP: drawn with a few draw calls (" + EspRenderer.drawCalls + ")", EspRenderer.drawCalls > 0 && EspRenderer.drawCalls < 40);
            if (diamond != null) {
                SectionData data = ScanCache.get(ChunkSectionPos.asLong(diamond.nearest().getX() >> 4, diamond.nearest().getY() >> 4, diamond.nearest().getZ() >> 4));
                boolean onlyDiamond = data != null && data.espIdx.length == 8 && data.mesh() != null;
                if (onlyDiamond) {
                    // 12 cube edges of 2 blocks = 24 unit segments; 8 separate boxes would be 96.
                    int segments = data.mesh().layers[MeshData.ESP_LINES].length / 8;
                    check("Ore ESP: fused vein outline is 24 segments, not 96 (" + segments + ")", segments == 24);
                } else {
                    log.add("INFO diamond cube crosses a section border or shares it; edge count not checked");
                }
            }
            shot(c, "xray_ore_esp.png");
            run(() -> sw().setBlockState(rel(dx + 1, -8, dz + 1), Blocks.STONE.getDefaultState())); // break one
        });
        step(20, c -> {
            VeinMerger.Vein after = find(Blocks.DIAMOND_ORE);
            check("Ore ESP: breaking a block updates the vein within 1 s (8 -> 7)", after != null && after.count() == 7);
            run(() -> sw().setBlockState(rel(dx + 1, -8, dz + 1), Blocks.DIAMOND_ORE.getDefaultState()));
        });
        step(20, c -> {
            VeinMerger.Vein after = find(Blocks.DIAMOND_ORE);
            check("Ore ESP: placing it back updates within 1 s (7 -> 8)", after != null && after.count() == 8);
            XrayConfig.get().style = "CORNERS";
        });
        step(20, c -> { shot(c, "xray_corners.png"); XrayConfig.get().style = "OUTLINE_FILL"; });
        // ----- Block ESP (and Chest ESP as a shortcut) -----
        step(60, c -> on(Feature.CHEST_ESP));
        step(5, c -> {
            check("Block ESP: MineMenu's Chest ESP switches it on", XrayConfig.get().blockEsp);
            check("Block ESP: the chest is found", find(Blocks.CHEST) != null);
            check("Block ESP: the spawner is found", find(Blocks.SPAWNER) != null);
            shot(c, "xray_block_esp.png");
        });
        // ----- Fluid View and Cave Finder -----
        step(60, c -> { XrayConfig.get().caveMaxY = 320; XrayConfig.get().set(XrayMode.FLUID_VIEW, true); XrayConfig.get().set(XrayMode.CAVE_FINDER, true); });
        step(5, c -> {
            boolean lava = false, cave = false;
            for (SectionData data : ScanCache.all()) {
                if (data.lava != null && data.mesh() != null && data.mesh().has(MeshData.FLUID_LINES)) lava = true;
                if (data.cave != null && data.mesh() != null && data.mesh().has(MeshData.CAVE_LINES)) cave = true;
            }
            check("Fluid View: lava found and drawn", lava);
            check("Cave Finder: cave air found and drawn", cave);
            shot(c, "xray_fluid_cave.png");
            XrayConfig.get().caveLargeOnly = true;
            XrayConfig.get().caveMinVolume = 5000;
        });
        step(10, c -> {
            shot(c, "xray_cave_large_only.png");
            XrayConfig.get().caveLargeOnly = false;
            XrayConfig.get().caveMaxY = 40;
            XrayConfig.get().set(XrayMode.FLUID_VIEW, false);
            XrayConfig.get().set(XrayMode.CAVE_FINDER, false);
        });
        // ----- Lava Warning: look at the platform block that has lava under it, "mine" it -----
        step(10, c -> {
            XrayConfig.get().set(XrayMode.LAVA_WARNING, true);
            tp(0, 0, 0, 180, 62);
        });
        step(5, c -> {
            if (c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit) {
                lavaUnder = hit.getBlockPos().down();
                run(() -> sw().setBlockState(lavaUnder, Blocks.LAVA.getDefaultState()));
            }
        });
        step(5, c -> c.options.attackKey.setPressed(true));
        step(5, c -> {
            check("Lava Warning: warns when mining next to lava", LavaWarning.showing());
            shot(c, "xray_lava_warning.png");
            c.options.attackKey.setPressed(false);
            if (c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
            if (lavaUnder != null) run(() -> { sw().setBlockState(lavaUnder, Blocks.STONE.getDefaultState()); sw().setBlockState(lavaUnder.up(), Blocks.STONE.getDefaultState()); });
            XrayConfig.get().set(XrayMode.LAVA_WARNING, false);
            tp(0, 0, 0, 180, 30);
        });
        // ----- Entity ESP replaces Mob Glow -----
        step(10, c -> { clearMobs(); a = spawn(EntityType.ZOMBIE, 3, -10, false); b = spawn(EntityType.COW, -3, -10, false); on(Feature.MOB_GLOW); });
        step(40, c -> {
            check("Entity ESP: MineMenu's Mob Glow switches it on", XrayConfig.get().entityEsp);
            check("Entity ESP: mobs drawn (" + OverlayRenderer.drawnEntities + ")", OverlayRenderer.drawnEntities >= 2);
            check("Entity ESP: no Glowing effect any more (no server-side glow)", srv(() -> !((LivingEntity) a).hasStatusEffect(StatusEffects.GLOWING)));
            shot(c, "xray_entity_esp.png");
            off(Feature.MOB_GLOW);
            off(Feature.CHEST_ESP);
            clearMobs();
        });
        // ----- Classic X-Ray -----
        step(5, c -> { num = stoneShare(c); cullingBefore = c.chunkCullingEnabled; press(XrayKeys.of(XrayMode.CLASSIC)); });
        step(80, c -> {
            check("Classic X-Ray: on, full bright, chunk culling off", ClassicXray.active() && com.minemenu.player.PlayerModules.forceFullBright && !c.chunkCullingEnabled);
            check("Classic X-Ray: stone is hidden, diamond is not", ClassicXray.skip(Blocks.STONE.getDefaultState()) && !ClassicXray.skip(Blocks.DIAMOND_ORE.getDefaultState()));
        });
        step(100, c -> {
            shot(c, "xray_classic.png");
            double now = stoneShare(c);
            check(String.format("Classic X-Ray: the stone floor is gone on screen (stone-grey %.0f%% -> %.0f%%)", num * 100, now * 100), num > 0.5 && now < num / 3);
        });
        step(5, c -> XrayConfig.get().classicOpacity = 30);
        step(120, c -> {
            check("Classic X-Ray: opacity 30% draws stone see-through", ClassicXray.seeThrough(Blocks.STONE.getDefaultState()));
            shot(c, "xray_classic_30.png");
            XrayConfig.get().classicOpacity = 0;
            press(XrayKeys.of(XrayMode.CLASSIC));
        });
        step(120, c -> {
            check("Classic X-Ray: off puts culling and brightness back", !ClassicXray.active() && c.chunkCullingEnabled == cullingBefore
                    && !com.minemenu.player.PlayerModules.forceFullBright);
            check("Classic X-Ray: chunks rebuilt (no rebuild left hanging)", !ClassicXray.rebuilding());
            shot(c, "xray_classic_off.png");
        });
        // ----- Presets: Diamonds Only drops gold; export/import -----
        step(60, c -> PresetManager.apply("Diamonds Only", true));
        step(5, c -> {
            check("Presets: Diamonds Only shows diamonds but not gold", find(Blocks.DIAMOND_ORE) != null && find(Blocks.GOLD_ORE) == null);
            String code = PresetManager.export("Diamonds Only");
            String name = PresetManager.importCode(code);
            check("Presets: share code export/import (" + name + ")", name != null && PresetManager.get(name) != null
                    && PresetManager.get(name).blocks.size() == PresetManager.get("Diamonds Only").blocks.size());
            if (name != null) PresetManager.delete(name);
            check("Presets: broken code refused", PresetManager.importCode("MXR1:not-base64!") == null);
            PresetManager.apply("All Ores", false);
        });
        // ----- Block editor: add the looked-at block, tags -----
        step(5, c -> { tp(0, 0, 0, 180, 62); });
        step(5, c -> {
            XrayActions.addLookedAt(c);
            check("Block editor: looked-at block added to the preset", PresetManager.active().find("minecraft:stone") != null);
            PresetManager.active().blocks.removeIf(r -> "minecraft:stone".equals(r.id));
            check("Block editor: tags resolve (#minecraft:diamond_ores = 2 blocks)", com.minexray.BlockFilter.resolve("#minecraft:diamond_ores").size() == 2);
            check("Block editor: deepslate twin follows the ore", com.minexray.BlockFilter.resolve("minecraft:iron_ore").size() == 2);
            PresetManager.changed(PresetManager.active());
            tp(0, 0, 0, 180, 30);
        });
        // ----- The tab and every sub-page at 1366x768, GUI Scale 2 -----
        for (XrayScreen.Page page : XrayScreen.Page.values()) {
            step(10, c -> c.setScreen(new XrayScreen(null, page, false)));
            step(10, c -> {
                check("MineXray tab: " + page + " page opens", c.currentScreen instanceof XrayScreen s && s.page() == page && s.content() != null);
                shot(c, "xray_page_" + page.name().toLowerCase() + ".png");
            });
        }
        step(5, c -> c.setScreen(null));
        // ----- Scanner: 128 blocks without freezing; waypoint, teleport, mine -----
        step(5, c -> { startRec(); ScanJob.start(128, null); });
        for (int i = 0; i < 90; i++) {
            step(20, c -> { if (!ScanJob.running()) skipWait(); });
        }
        step(5, c -> {
            double[] r = stopRec("Scan 128 (Ore ESP on)");
            check("Scanner: 128-block scan finished", !ScanJob.running());
            check("Scanner: the game kept running during it (1% low " + Math.round(r[1]) + " FPS)", r[1] >= 3);
            diamond = findIn(ScanJob.results(), Blocks.DIAMOND_ORE);
            check("Scanner: lists the diamond vein", diamond != null);
            if (diamond != null) {
                XrayActions.waypoint(diamond);
                XrayActions.show(diamond);
            }
        });
        step(10, c -> {
            boolean saved = false;
            for (int i = 0; i < Waypoints.MAX; i++) {
                if (Waypoints.get(i) != null && Waypoints.get(i).name.contains("Diamond")) saved = true;
            }
            check("Scanner: Mark as waypoint saved it", saved);
            check("Scanner: Show highlights it", OverlayRenderer.highlights() > 0);
            if (diamond != null) XrayActions.teleport(diamond);
        });
        step(20, c -> {
            BlockPos at = c.player.getBlockPos();
            check("Scanner: Teleport puts you in safe air at the vein (" + at.toShortString() + ")", diamond != null
                    && Math.abs(at.getX() - diamond.nearest().getX()) <= 6 && Math.abs(at.getZ() - diamond.nearest().getZ()) <= 6
                    && srv(() -> sw().getBlockState(at).getCollisionShape(sw(), at).isEmpty() && sw().getBlockState(at.up()).getCollisionShape(sw(), at.up()).isEmpty())
                    && !c.player.isInLava());
            diamondsBefore = invCount(Items.DIAMOND);
            if (diamond != null) XrayActions.mine(diamond);
        });
        step(20, c -> {
            check("Vein Miner: Mine whole vein gives the diamonds (" + (invCount(Items.DIAMOND) - diamondsBefore) + ")", invCount(Items.DIAMOND) - diamondsBefore >= 8);
            tp(0, 0, 0, 180, 30);
        });
        // ----- Dimension switch: preset and cache -----
        step(5, c -> run(() -> {
            ServerWorld nether = server.getWorld(World.NETHER);
            BlockPos p = new BlockPos(0, 100, 0);
            for (BlockPos q : BlockPos.iterate(p.add(-2, -1, -2), p.add(2, 3, 2))) nether.setBlockState(q, q.getY() == p.getY() - 1 ? Blocks.OBSIDIAN.getDefaultState() : Blocks.AIR.getDefaultState());
            host().teleport(nether, 0.5, 100, 0.5, Set.of(), 0, 0);
        }));
        step(120, c -> {
            check("Dimension: Nether switches to the Netherite Hunt preset", "Netherite Hunt".equals(XrayConfig.get().activePreset));
            run(() -> host().teleport(server.getOverworld(), base.getX() + 0.5, base.getY(), base.getZ() + 0.5, Set.of(), 180, 30));
        });
        step(120, c -> check("Dimension: back in the Overworld, All Ores again", "All Ores".equals(XrayConfig.get().activePreset)));
        // ----- Safe Mode per mode, profiles, all off = nothing kept -----
        step(5, c -> {
            XrayConfig.get().set(XrayMode.FLUID_VIEW, true);
            MineXrayClient.safe(XrayMode.FLUID_VIEW, () -> { throw new IllegalStateException("test: forced error in Fluid View"); });
            check("Safe Mode: an error turns just that mode off", !XrayConfig.get().fluidView && XrayConfig.get().oreEsp);
            com.minemenu.ui.Profiles.saveCurrent("xraytest");
            allModesOff();
            com.minemenu.ui.Profiles.load("xraytest");
            check("Profiles: MineXray modes saved and loaded with a profile", XrayConfig.get().oreEsp);
            com.minemenu.ui.Profiles.delete("xraytest");
            allModesOff();
        });
        step(40, c -> check("All modes off: no scan data or GPU buffers kept", ScanCache.size() == 0 && EspRenderer.drawCalls == 0));
    }
}
