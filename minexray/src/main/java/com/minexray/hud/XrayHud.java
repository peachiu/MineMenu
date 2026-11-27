package com.minexray.hud;

import com.minexray.XrayHost;
import com.minexray.MineXrayClient;
import com.minexray.XrayActions;
import com.minexray.XrayConfig;
import com.minexray.XrayMode;
import com.minexray.mode.ClassicXray;
import com.minexray.mode.LavaWarning;
import com.minexray.scan.VeinMerger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MineXray's HUD, down the left side: which modes are on, Ore Counter, Nearest Ore Arrow, Y-Level Guide and
 * Session Stats (each can be switched off), plus "Rebuilding chunks..." and the Lava Warning in the middle.
 */
public final class XrayHud {
    private static final int MAX_COUNTER_LINES = 6;

    private XrayHud() {
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
            return;
        }
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        if (ClassicXray.rebuilding()) {
            context.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("minexray.rebuilding").formatted(Formatting.GRAY),
                    width / 2, height / 2 + 20, 0xFFFFFF);
        }
        if (!MineXrayClient.allowed()) {
            return;
        }
        com.minexray.render.LabelRenderer.render(context);
        if (MineXrayClient.active(XrayMode.LAVA_WARNING) && LavaWarning.showing()) {
            boolean blink = (System.currentTimeMillis() / 250) % 2 == 0;
            context.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("minexray.lava_warning.text")
                    .formatted(blink ? Formatting.RED : Formatting.GOLD, Formatting.BOLD), width / 2, height / 2 - 24, 0xFFFFFF);
        }
        XrayConfig config = XrayConfig.get();
        List<Text> lines = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        if (config.hudIndicator) {
            List<String> on = new ArrayList<>();
            for (XrayMode mode : XrayMode.values()) {
                if (MineXrayClient.active(mode)) {
                    on.add(mode.displayName());
                }
            }
            if (!on.isEmpty()) {
                add(lines, icons, Text.literal("◉ " + String.join(" · ", on)).formatted(Formatting.AQUA), null);
            }
        }
        boolean esp = MineXrayClient.active(XrayMode.ORE_ESP) || MineXrayClient.active(XrayMode.BLOCK_ESP);
        List<VeinMerger.Vein> veins = VeinMerger.veins();
        if (esp && config.hudCounter) {
            List<OreCounter.Line> counter = OreCounter.lines(veins);
            for (int i = 0; i < Math.min(MAX_COUNTER_LINES, counter.size()); i++) {
                OreCounter.Line line = counter.get(i);
                add(lines, icons, Text.literal(XrayHost.trf("minexray.hud.counter_line", "%s: %d (%d veins)", line.block().getName().getString(),
                        line.blocks(), line.veins())).withColor(line.color()), new ItemStack(line.block().asItem()));
            }
        }
        if (esp && config.hudArrow) {
            VeinMerger.Vein nearest = NearestOreArrow.nearest(veins);
            if (nearest != null) {
                int dy = NearestOreArrow.dy(client, nearest);
                String height2 = dy == 0 ? "" : dy > 0 ? " ▲" + dy : " ▼" + (-dy);
                add(lines, icons, Text.literal(NearestOreArrow.arrow(client, nearest) + " " + XrayHost.trf("minexray.hud.nearest", "%s %dm%s",
                        XrayActions.name(nearest), Math.round(nearest.distance()), height2)).withColor(nearest.color()), new ItemStack(nearest.block().asItem()));
            }
        }
        if (config.hudYGuide) {
            int y = client.player.getBlockY();
            String dimension = client.world.getRegistryKey().getValue().toString();
            add(lines, icons, Text.translatable("minexray.hud.y_guide", y).formatted(Formatting.GRAY), null);
            for (YLevelGuide.Range range : YLevelGuide.RANGES) {
                if (!range.dimension().equals(dimension)) {
                    continue;
                }
                boolean here = YLevelGuide.inRange(range, y);
                add(lines, icons, Text.literal(XrayHost.trf("minexray.hud.y_range", "%s: %d to %d, best %d", range.block().getName().getString(),
                        range.min(), range.max(), range.best())).formatted(here ? Formatting.GREEN : Formatting.DARK_GRAY), new ItemStack(range.block().asItem()));
            }
        }
        if (config.hudStats) {
            add(lines, icons, Text.translatable("minexray.hud.stats", SessionStats.total(), SessionStats.lavaWarnings, SessionStats.minutes())
                    .formatted(Formatting.GRAY), null);
            for (Map.Entry<net.minecraft.block.Block, Integer> entry : SessionStats.mined().entrySet()) {
                add(lines, icons, Text.literal("  " + entry.getKey().getName().getString() + " ×" + entry.getValue()).formatted(Formatting.DARK_GRAY), null);
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        int lineHeight = 11;
        int top = Math.max(40, height / 2 - lines.size() * lineHeight / 2);
        int textWidth = 0;
        for (Text line : lines) {
            textWidth = Math.max(textWidth, client.textRenderer.getWidth(line));
        }
        context.fill(2, top - 2, 2 + 16 + textWidth + 6, top + lines.size() * lineHeight + 1, 0x60000000);
        for (int i = 0; i < lines.size(); i++) {
            int y = top + i * lineHeight;
            ItemStack icon = icons.get(i);
            if (icon != null && !icon.isEmpty()) {
                context.getMatrices().push();
                context.getMatrices().translate(4, y - 1, 0);
                context.getMatrices().scale(0.625f, 0.625f, 1f);
                context.drawItem(icon, 0, 0);
                context.getMatrices().pop();
            }
            context.drawTextWithShadow(client.textRenderer, lines.get(i), 18, y, 0xFFFFFF);
        }
    }

    private static void add(List<Text> lines, List<ItemStack> icons, Text text, ItemStack icon) {
        lines.add(text);
        icons.add(icon);
    }

    /** Minimap layer: highlighted veins as coloured dots. */
    public static void minimapLayer(DrawContext context, MinecraftClient client, int cx, int cy, double scale, int size) {
        if (!XrayConfig.get().minimapLayer || client.player == null
                || !(MineXrayClient.active(XrayMode.ORE_ESP) || MineXrayClient.active(XrayMode.BLOCK_ESP))) {
            return;
        }
        double half = size / 2.0 - 2;
        for (VeinMerger.Vein vein : VeinMerger.veins()) {
            double dx = (vein.cx() - client.player.getX()) * scale;
            double dz = (vein.cz() - client.player.getZ()) * scale;
            if (Math.abs(dx) < half && Math.abs(dz) < half) {
                context.fill(cx + (int) dx - 1, cy + (int) dz - 1, cx + (int) dx + 1, cy + (int) dz + 1, 0xFF000000 | vein.color());
            }
        }
    }
}
