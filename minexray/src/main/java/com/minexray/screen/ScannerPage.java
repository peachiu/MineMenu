package com.minexray.screen;

import com.minexray.XrayHost;
import com.minexray.scan.ScanJob;
import com.minexray.XrayActions;
import com.minexray.scan.ScanCache;
import com.minexray.scan.SectionScanner;
import com.minexray.scan.VeinMerger;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scanner: "Scan now" lists every vein of the preset's blocks within the radius (up to 128), scanned in the
 * background so the game keeps running. Per vein: mark as waypoint, show it for 30 s, teleport next to it
 * (singleplayer), or mine it whole. Sort and filter the list; a summary per kind on top.
 */
final class ScannerPage implements XrayPage {
    private static final int ROW = 22;
    private static final String[] SORTS = {"DISTANCE", "SIZE", "TYPE"};
    private static int radius = 64;
    private static String sort = "DISTANCE";
    private static Block filter;
    private static int scroll;

    private final boolean autoStart;
    private final List<VeinMerger.Vein> shown = new ArrayList<>();
    private int x;
    private int width;
    private int listTop;
    private int visibleRows;
    private int headerY;

    ScannerPage(boolean autoStart) {
        this.autoStart = autoStart;
    }

    @Override
    public void init(XrayScreen screen, int x, int y, int width, int height) {
        List<VeinMerger.Vein> results = ScanJob.results();
        boolean scanning = ScanJob.running();
        this.x = x;
        this.width = width;
        if (autoStart && !scanning && results.isEmpty()) {
            start(screen);
            scanning = true;
        }
        screen.add(Widgets.slider("minexray.scanner.radius", 16, 128, 8, () -> radius, v -> radius = v, x, y, 140, "minexray.unit.blocks", null));
        ButtonWidget scan = screen.add(ButtonWidget.builder(Text.translatable(scanning ? "minexray.scanner.scanning" : "minexray.scanner.scan")
                .formatted(Formatting.GREEN), b -> {
            start(screen);
            screen.rebuild();
        }).dimensions(x + 144, y, 90, 20).build());
        scan.active = !scanning;
        screen.add(Widgets.cycle("minexray.scanner.sort", SORTS, () -> sort, v -> sort = v, x + 238, y, 110, screen::rebuild));
        List<Block> kinds = kinds();
        screen.add(ButtonWidget.builder(Text.translatable("minexray.scanner.filter").append(": ")
                .append(filter == null ? Text.translatable("minexray.scanner.filter.all") : filter.getName()).formatted(Formatting.YELLOW), b -> {
            int i = filter == null ? -1 : kinds.indexOf(filter);
            filter = i + 1 >= kinds.size() ? null : kinds.get(i + 1);
            scroll = 0;
            screen.rebuild();
        }).dimensions(x + 352, y, Math.max(60, Math.min(150, x + width - (x + 352))), 20).build());
        headerY = y + 24;
        listTop = headerY + 24;
        visibleRows = Math.max(1, (y + height - listTop) / ROW);
        shown.clear();
        for (VeinMerger.Vein vein : results) {
            if (filter == null || vein.block() == filter) {
                shown.add(vein);
            }
        }
        switch (sort) {
            case "SIZE" -> shown.sort((a, b) -> Integer.compare(b.count(), a.count()));
            case "TYPE" -> shown.sort((a, b) -> XrayActions.name(a).compareTo(XrayActions.name(b)));
            default -> shown.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        }
        scroll = Math.max(0, Math.min(scroll, shown.size() - visibleRows));
        int buttons = 4 * 40 + 3 * 2;
        for (int i = scroll; i < Math.min(shown.size(), scroll + visibleRows); i++) {
            VeinMerger.Vein vein = shown.get(i);
            int ry = listTop + (i - scroll) * ROW;
            int bx = x + width - buttons;
            ButtonWidget waypoint = screen.add(ButtonWidget.builder(Text.translatable("minexray.scanner.waypoint"), b -> XrayActions.waypoint(vein))
                    .dimensions(bx, ry, 40, 20).tooltip(Tooltip.of(Text.translatable(XrayActions.waypointsAvailable()
                            ? "minexray.scanner.waypoint.tooltip" : "minexray.scanner.waypoint.needs_minemenu"))).build());
            waypoint.active = XrayActions.waypointsAvailable();
            screen.add(ButtonWidget.builder(Text.translatable("minexray.scanner.show"), b -> XrayActions.show(vein))
                    .dimensions(bx + 42, ry, 40, 20).tooltip(Tooltip.of(Text.translatable("minexray.scanner.show.tooltip"))).build());
            screen.add(ButtonWidget.builder(Text.translatable("minexray.scanner.teleport"), b -> {
                XrayActions.teleport(vein);
                screen.close();
            }).dimensions(bx + 84, ry, 40, 20).tooltip(Tooltip.of(Text.translatable("minexray.scanner.teleport.tooltip"))).build());
            screen.add(ButtonWidget.builder(Text.translatable("minexray.scanner.mine"), b -> XrayActions.mine(vein))
                    .dimensions(bx + 126, ry, 40, 20).tooltip(Tooltip.of(Text.translatable("minexray.scanner.mine.tooltip"))).build());
        }
    }

    private static List<Block> kinds() {
        List<Block> kinds = new ArrayList<>();
        for (VeinMerger.Vein vein : ScanJob.results()) {
            if (!kinds.contains(vein.block())) {
                kinds.add(vein.block());
            }
        }
        return kinds;
    }

    private static void start(XrayScreen screen) {
        scroll = 0;
        ScanJob.start(radius, () -> {
            if (screen.mc().currentScreen == screen) {
                screen.rebuild();
            }
        });
    }

    @Override
    public void render(XrayScreen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        var text = screen.font();
        List<VeinMerger.Vein> results = ScanJob.results();
        if (ScanJob.running()) {
            context.drawTextWithShadow(text, Text.translatable("minexray.scanner.progress", ScanCache.size(), SectionScanner.pending())
                    .formatted(Formatting.AQUA), x, headerY, 0xFFFFFF);
            return;
        }
        // Summary per kind.
        Map<Block, int[]> summary = new LinkedHashMap<>();
        for (VeinMerger.Vein vein : results) {
            int[] s = summary.computeIfAbsent(vein.block(), b -> new int[2]);
            s[0] += vein.count();
            s[1]++;
        }
        StringBuilder line = new StringBuilder();
        for (Map.Entry<Block, int[]> entry : summary.entrySet()) {
            if (!line.isEmpty()) {
                line.append("  ");
            }
            line.append(entry.getKey().getName().getString()).append(" ").append(entry.getValue()[0]).append("/").append(entry.getValue()[1]);
        }
        Text summaryText = results.isEmpty() ? Text.translatable("minexray.scanner.none") : Text.translatable("minexray.scanner.summary",
                results.size(), text.trimToWidth(line.toString(), width - 120));
        context.drawTextWithShadow(text, summaryText.copy().formatted(Formatting.GRAY), x, headerY, 0xFFFFFF);
        context.drawTextWithShadow(text, Text.translatable("minexray.scanner.columns").formatted(Formatting.DARK_GRAY), x + 20, headerY + 12, 0xFFFFFF);
        var player = screen.mc().player;
        int textRight = x + width - (4 * 40 + 3 * 2) - 6;
        for (int i = scroll; i < Math.min(shown.size(), scroll + visibleRows); i++) {
            VeinMerger.Vein vein = shown.get(i);
            int ry = listTop + (i - scroll) * ROW;
            context.drawItem(new ItemStack(vein.block().asItem()), x, ry + 2);
            String where = vein.nearest().getX() + " " + vein.nearest().getY() + " " + vein.nearest().getZ();
            String direction = player == null ? "" : direction(vein.cx() - player.getX(), vein.cz() - player.getZ());
            int dy = player == null ? 0 : (int) Math.round(vein.cy() - player.getEyeY());
            String row = XrayHost.trf("minexray.scanner.row", "%s ×%d · %s · %dm %s %s", XrayActions.name(vein), vein.count(), where,
                    Math.round(player == null ? vein.distance() : Math.sqrt(player.squaredDistanceTo(vein.cx(), vein.cy(), vein.cz()))), direction,
                    dy == 0 ? "" : dy > 0 ? "▲" + dy : "▼" + (-dy));
            context.drawTextWithShadow(text, text.trimToWidth(row, textRight - x - 20), x + 20, ry + 6, 0xFF000000 | vein.color());
        }
        if (shown.size() > visibleRows) {
            context.drawTextWithShadow(text, Text.literal((scroll + 1) + "-" + Math.min(shown.size(), scroll + visibleRows) + " / " + shown.size())
                    .formatted(Formatting.DARK_GRAY), x + width - 70, headerY + 12, 0xFFFFFF);
        }
    }

    /** N, NE, E... (north is -Z). */
    static String direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return XrayHost.tr("minexray.dir." + names[Math.floorMod((int) Math.round(angle / 45), 8)].toLowerCase(java.util.Locale.ROOT),
                names[Math.floorMod((int) Math.round(angle / 45), 8)]);
    }

    @Override
    public boolean scrolled(XrayScreen screen, double amount) {
        int before = scroll;
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - visibleRows), scroll - (int) Math.signum(amount) * 2));
        if (before != scroll) {
            screen.rebuild();
        }
        return true;
    }
}
