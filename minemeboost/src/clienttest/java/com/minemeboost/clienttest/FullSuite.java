package com.minemeboost.clienttest;

import com.mineboost.BoostConfig;
import com.mineboost.Impostors;
import com.mineboost.Preset;
import com.mineboost.renderscale.Sharpen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import com.minemenu.ServerCheats;
import java.nio.file.Path;
import java.util.Map;

/** The full suite: everything from 3.4 (PlayerTests, WorldTests) plus the 3.5 features. */
class FullSuite extends WorldTests {
    private com.minemenu.build.UndoStack.Entry igloo;

    FullSuite() {
        super("full");
    }

    @Override
    protected void build() {
        buildPlayer();
        buildWorld();
        build35();
        step(5, c -> {});
    }

    /** Edge contrast of the middle of the screen: higher = sharper. */
    private static double sharpness(MinecraftClient c) {
        try (NativeImage image = ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            int w = image.getWidth(), h = image.getHeight();
            double total = 0;
            int n = 0;
            for (int y = h / 4; y < h * 3 / 4; y++) {
                for (int x = w / 4; x < w * 3 / 4 - 1; x++) {
                    int p = image.getColor(x, y), q = image.getColor(x + 1, y);
                    total += Math.abs((p & 0xFF) - (q & 0xFF)) + Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF));
                    n++;
                }
            }
            return total / n;
        }
    }

    private void build35() {
        // ===== 3.5: Render Scale + Sharpen (CAS) =====
        step(5, c -> { if (c.player.isDead()) c.player.requestRespawn(); });
        step(40, c -> { c.setScreen(null); on(com.minemenu.Feature.GOD_MODE); run(() -> { for (var q : net.minecraft.util.math.BlockPos.iterate(rel(-30, -1, -40), rel(30, -1, 30))) sw().setBlockState(q, net.minecraft.block.Blocks.STONE.getDefaultState()); host().setHealth(20); }); });
        step(5, c -> { check("3.5 tests start with the player alive", !c.player.isDead()); clearMobs(); clearArea(); tp(0, 0, 0, 180, 10);
            run(() -> { for (int i = 0; i < 12; i++) sw().setBlockState(rel(-6 + i, 0, -6 - (i % 3)), (i % 2 == 0 ? net.minecraft.block.Blocks.BOOKSHELF : net.minecraft.block.Blocks.OAK_LOG).getDefaultState()); }); });
        step(20, c -> { BoostConfig.get().renderScale = 75; BoostConfig.get().renderScaleSharpen = 0; });
        step(20, c -> { num = sharpness(c); shot(c, "cas_75_off.png"); BoostConfig.get().renderScaleSharpen = 100; });
        step(20, c -> {
            num2 = sharpness(c);
            shot(c, "cas_75_on.png");
            log.add(String.format("sharpen: edge contrast %.2f without, %.2f with CAS", num, num2));
            check("Sharpen (CAS): shader loaded", Sharpen.available());
            check("Sharpen (CAS): the picture at 75% gets sharper", num2 > num * 1.05);
            BoostConfig.get().renderScale = 100;
            BoostConfig.get().renderScaleSharpen = 50;
        });
        // ===== 3.5: Presets use the A/B results =====
        step(5, c -> {
            Map<String, Integer> gains = BoostConfig.get().abGains;
            gains.clear();
            check("Presets without an A/B test: Toaster 75%, Potato 100%, Balanced 100%",
                    Preset.TOASTER.renderScale() == 75 && Preset.POTATO.renderScale() == 100 && Preset.BALANCED.renderScale() == 100);
            gains.put("Render Scale 75%", 12);
            gains.put("Render Scale 50%", 25);
            check("Presets after an A/B test with big gains: Toaster 50%, Potato 75%, Intel iGPU 75%, High 100%",
                    Preset.TOASTER.renderScale() == 50 && Preset.POTATO.renderScale() == 75 && Preset.INTEL_IGPU.renderScale() == 75 && Preset.HIGH.renderScale() == 100);
            gains.put("Render Scale 75%", 2);
            gains.put("Render Scale 50%", 4);
            check("Presets after an A/B test with no gain: all 100%", Preset.TOASTER.renderScale() == 100 && Preset.INTEL_IGPU.renderScale() == 100);
            log.add("preset note: " + Preset.TOASTER.renderScaleNote());
            gains.clear();
        });
        // ===== 3.5: Impostors =====
        step(5, c -> {
            for (int i = 0; i < 16; i++) {
                ZombieEntity z = spawn(EntityType.ZOMBIE, -12 + i * 1.6, -32 - (i % 4) * 2, false);
                if (i % 2 == 0) run(() -> { z.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET)); z.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE)); z.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)); });
            }
            for (int i = 0; i < 6; i++) spawn(EntityType.COW, -10 + i * 4, -28, false);
            spawn(EntityType.CREEPER, 6, -26, false);
            tp(0, 0, 0, 180, 5);
        });
        step(40, c -> shot(c, "impostors_off.png"));
        step(5, c -> BoostConfig.get().impostorDistance = 24);
        step(60, c -> {});
        step(5, c -> {
            shot(c, "impostors_on.png");
            log.add("impostors: " + Impostors.count() + " kinds photographed");
            check("Impostors: far mobs got pictures (at least zombie, armored zombie and cow)", Impostors.count() >= 3);
            try { Impostors.savePictures(Path.of("clienttest", "impostors")); } catch (Exception e) { log.add("impostor pictures: " + e); }
        });
        step(5, c -> tp(0, 0, -20, 180, 5)); // walk up to them: within 24 blocks they're drawn normally again
        step(30, c -> shot(c, "impostors_near.png"));
        step(5, c -> { BoostConfig.get().impostorDistance = 0; clearMobs(); tp(0, 0, 0, 180, 0); });
        step(20, c -> check("Impostors: turning it off frees the pictures", Impostors.count() == 0));
        // ===== 3.5: vanilla structures can be undone =====
        step(5, c -> { clearArea(); tp(0, 0, 0, 180, 40); run(() -> com.minemenu.build.VanillaStructures.place(host(), "minecraft:igloo", rel(0, 0, -12))); });
        step(20, c -> {
            var entry = com.minemenu.build.UndoStack.last();
            igloo = entry;
            log.add("igloo: " + (entry == null ? "nothing to undo" : entry.name() + ", " + entry.before().size() + " blocks changed"));
            check("Vanilla structure: placed with an Undo step", entry != null && entry.name().contains("igloo") && !entry.after().isEmpty());
            action(ServerCheats.Action.UNDO);
        });
        step(120, c -> {});
        step(5, c -> {
            int wrong = igloo == null ? -1 : srv(() -> {
                int n = 0;
                for (var change : igloo.before()) if (sw().getBlockState(change.pos()) != change.state()) n++;
                return n;
            });
            log.add("igloo undo: " + wrong + " blocks differ from before");
            check("Vanilla structure: Undo puts every block back", wrong == 0);
        });
        // ===== 3.5: Portuguese (Portugal), at 1366x768 with GUI Scale 2 =====
        step(5, c -> {
            c.getWindow().setWindowedSize(1366, 768);
            c.options.getGuiScale().setValue(2);
            c.options.language = "pt_pt";
            c.getLanguageManager().setLanguage("pt_pt");
            c.reloadResources();
        });
        step(300, c -> {});
        step(5, c -> {
            c.onResolutionChanged();
            String fly = com.minemenu.Feature.FLY.displayName();
            String scale = net.minecraft.text.Text.translatable("mineboost.option.render_scale").getString();
            log.add("pt_pt: Fly = " + fly + ", Render Scale = " + scale + ", window " + c.getWindow().getWidth() + "x" + c.getWindow().getHeight());
            check("pt_PT: hacks and options are translated (Voar, Escala render)", fly.equals("Voar") && scale.equals("Escala render"));
            check("pt_PT: a menu entry name is translated (Esquemas...)", com.minemenu.ui.MenuEntries.byId("screen:schematics").name().equals("Esquemas..."));
        });
        for (String theme : new String[]{"dark", "light", "glass"}) {
            step(5, c -> { com.minemenu.core.CheatConfig.get().theme = theme; c.setScreen(new com.minemenu.MineMenuScreen()); });
            step(15, c -> { if (c.currentScreen instanceof com.minemenu.ui.WhatsNewScreen w) { shot(c, "pt_whats_new.png"); w.close(); } });
            step(15, c -> shot(c, "pt_1366_menu_" + theme + ".png"));
        }
        step(5, c -> c.setScreen(new com.mineboost.BoostScreen(null, c.options)));
        step(15, c -> { shot(c, "pt_1366_mineboost.png"); c.setScreen(new com.mineboost.tools.BoostToolsScreen(null)); });
        step(15, c -> { shot(c, "pt_1366_tools.png"); c.setScreen(new com.minemenu.build.SchematicBrowserScreen(null)); });
        step(15, c -> { shot(c, "pt_1366_schematics.png"); c.setScreen(new com.minemenu.ui.CommandPalette(null)); });
        step(5, c -> { for (char ch : "voar".toCharArray()) c.currentScreen.charTyped(ch, 0); });
        step(10, c -> { shot(c, "pt_1366_palette.png"); c.setScreen(null);
            com.minemenu.core.CheatConfig.get().theme = "dark";
            c.options.language = "en_us";
            c.getLanguageManager().setLanguage("en_us");
            c.reloadResources(); });
        step(600, c -> {});
        step(5, c -> check("Back to English after pt_PT", com.minemenu.Feature.FLY.displayName().equals("Fly")));
        // ===== 3.5: schematics browser =====
        step(5, c -> c.setScreen(new com.minemenu.build.SchematicBrowserScreen(null)));
        step(10, c -> {
            var screen = (com.minemenu.build.SchematicBrowserScreen) c.currentScreen;
            log.add("schematics listed: " + screen.shownNames());
            check("Schematics browser: lists the saved schematic", screen.shownNames().contains("test house"));
            screen.select("test house");
        });
        step(10, c -> {
            var screen = (com.minemenu.build.SchematicBrowserScreen) c.currentScreen;
            var p = screen.preview();
            log.add(p == null ? "no preview" : "preview: size " + p.size + ", " + p.blocks + " blocks, top " + p.topWidth + "x" + p.topHeight);
            check("Schematics browser: preview with size and blocks", p != null && p.blocks > 0 && (p.size.getX() == 5 || p.size.getZ() == 5));
            shot(c, "schematics_browser.png");
            c.setScreen(null);
        });
    }
}
