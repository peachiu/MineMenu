package com.minexray.screen;

import com.minexray.BlockFilter;
import com.minexray.BlockRule;
import com.minexray.EspCategory;
import com.minexray.PresetManager;
import com.minexray.XrayActions;
import com.minexray.XrayPreset;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Block editor for the active preset: every block in the game (modded too) with a search box, its icon, name
 * and id. Per block: kept in Classic (V), shown by ESP (E), colour and category. Add by search, by the block you
 * look at, by the item in your hand, or a tag ("#minecraft:diamond_ores"). Deepslate ores share the normal
 * ore's line.
 */
final class BlocksPage implements XrayPage {
    private static final int ROW = 22;
    private static String search = "";
    private static boolean onlyPreset = true;
    private static int scroll;
    private static boolean refocus;
    private static List<Block> allBlocks;

    private final List<Object> rows = new ArrayList<>();
    private int x;
    private int listTop;
    private int width;
    private int visibleRows;
    private TextFieldWidget tagField;

    @Override
    public void init(XrayScreen screen, int x, int y, int width, int height) {
        this.x = x;
        this.width = width;
        XrayPreset preset = PresetManager.active();
        TextFieldWidget searchField = new TextFieldWidget(screen.font(), x, y, Math.min(150, width / 3), 18, Text.translatable("minexray.blocks.search"));
        searchField.setPlaceholder(Text.translatable("minexray.blocks.search").formatted(Formatting.DARK_GRAY));
        searchField.setText(search);
        searchField.setChangedListener(text -> {
            search = text;
            scroll = 0;
            refocus = true;
            screen.rebuild();
        });
        screen.add(searchField);
        if (refocus) {
            refocus = false;
            screen.setFocused(searchField);
        }
        int bx = x + searchField.getWidth() + 4;
        screen.add(ButtonWidget.builder(Text.translatable(onlyPreset ? "minexray.blocks.in_preset" : "minexray.blocks.all_blocks"), b -> {
            onlyPreset = !onlyPreset;
            scroll = 0;
            screen.rebuild();
        }).dimensions(bx, y - 1, 90, 20).build());
        bx += 94;
        screen.add(ButtonWidget.builder(Text.translatable("minexray.blocks.add_looked"), b -> {
            XrayActions.addLookedAt(screen.mc());
            screen.rebuild();
        }).dimensions(bx, y - 1, Math.max(60, (x + width - bx) / 2 - 2), 20).tooltip(Tooltip.of(Text.translatable("minexray.blocks.add_looked.tooltip"))).build());
        screen.add(ButtonWidget.builder(Text.translatable("minexray.blocks.add_held"), b -> {
            XrayActions.addHeld(screen.mc());
            screen.rebuild();
        }).dimensions(bx + Math.max(60, (x + width - bx) / 2 - 2) + 4, y - 1, Math.max(60, (x + width - bx) / 2 - 2), 20).build());
        int y2 = y + 22;
        tagField = new TextFieldWidget(screen.font(), x, y2, Math.min(200, width - 70), 18, Text.translatable("minexray.blocks.tag"));
        tagField.setPlaceholder(Text.literal("#minecraft:diamond_ores").formatted(Formatting.DARK_GRAY));
        screen.add(tagField);
        screen.add(ButtonWidget.builder(Text.translatable("minexray.blocks.add_tag"), b -> addTag(screen, preset))
                .dimensions(x + tagField.getWidth() + 4, y2 - 1, 62, 20).build());
        listTop = y2 + 24;
        visibleRows = Math.max(1, (y + height - listTop) / ROW);
        collectRows(preset);
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows));
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            addRowButtons(screen, preset, rows.get(i), listTop + (i - scroll) * ROW);
        }
    }

    private void collectRows(XrayPreset preset) {
        rows.clear();
        String q = search.toLowerCase(Locale.ROOT).trim();
        if (onlyPreset) {
            for (BlockRule rule : preset.blocks) {
                if (rule.isTag() && (q.isEmpty() || rule.id.contains(q))) {
                    rows.add(rule);
                }
            }
        }
        if (allBlocks == null) {
            allBlocks = new ArrayList<>();
            for (Block block : Registries.BLOCK) {
                Identifier id = Registries.BLOCK.getId(block);
                if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR || BlockFilter.groupOf(block) != block) {
                    continue; // deepslate ores are on their normal ore's line
                }
                allBlocks.add(block);
            }
            allBlocks.sort((a, b) -> Registries.BLOCK.getId(a).toString().compareTo(Registries.BLOCK.getId(b).toString()));
        }
        for (Block block : allBlocks) {
            String id = Registries.BLOCK.getId(block).toString();
            if (onlyPreset && preset.find(id) == null) {
                continue;
            }
            if (!q.isEmpty() && !id.contains(q) && !block.getName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            rows.add(block);
        }
    }

    private void addRowButtons(XrayScreen screen, XrayPreset preset, Object row, int y) {
        String id = row instanceof BlockRule rule ? rule.id : Registries.BLOCK.getId((Block) row).toString();
        BlockRule rule = preset.find(id);
        int right = x + width;
        int bx = right - 22 - 4 - 22 - 4 - 22 - 4 - 92;
        Runnable changed = () -> PresetManager.changed(preset);
        boolean visible = rule != null && rule.visible;
        boolean esp = rule != null && rule.esp;
        screen.add(ButtonWidget.builder(Text.literal("V").formatted(visible ? Formatting.GREEN : Formatting.DARK_GRAY), b -> {
            BlockRule r = ruleFor(preset, id);
            r.visible = !r.visible;
            tidy(preset, r);
            changed.run();
            screen.rebuild();
        }).dimensions(bx, y, 22, 20).tooltip(Tooltip.of(Text.translatable("minexray.blocks.visible.tooltip"))).build());
        bx += 26;
        screen.add(ButtonWidget.builder(Text.literal("E").formatted(esp ? Formatting.GREEN : Formatting.DARK_GRAY), b -> {
            BlockRule r = ruleFor(preset, id);
            r.esp = !r.esp;
            tidy(preset, r);
            changed.run();
            screen.rebuild();
        }).dimensions(bx, y, 22, 20).tooltip(Tooltip.of(Text.translatable("minexray.blocks.esp.tooltip"))).build());
        bx += 26;
        if (rule != null) {
            screen.add(Widgets.color(() -> colorOf(rule, id), c -> rule.color = c, bx, y, 22, changed));
            screen.add(ButtonWidget.builder(Text.literal(EspCategory.parse(rule.category).displayName()), b -> {
                EspCategory[] all = EspCategory.values();
                rule.category = all[(EspCategory.parse(rule.category).ordinal() + 1) % all.length].name();
                changed.run();
                screen.rebuild();
            }).dimensions(bx + 26, y, 92, 20).tooltip(Tooltip.of(Text.translatable("minexray.blocks.category.tooltip"))).build());
        }
    }

    private static int colorOf(BlockRule rule, String id) {
        if (rule.color >= 0) {
            return rule.color;
        }
        List<Block> blocks = BlockFilter.resolve(id);
        return blocks.isEmpty() ? 0xFFFFFF : blocks.get(0).getDefaultMapColor().color;
    }

    private static BlockRule ruleFor(XrayPreset preset, String id) {
        BlockRule rule = preset.find(id);
        if (rule == null) {
            rule = new BlockRule(id, -1, EspCategory.ORE.name(), false, false);
            preset.blocks.add(rule);
        }
        return rule;
    }

    /** A line with neither box ticked is taken off the list. */
    private static void tidy(XrayPreset preset, BlockRule rule) {
        if (!rule.visible && !rule.esp) {
            preset.blocks.remove(rule);
        }
    }

    private void addTag(XrayScreen screen, XrayPreset preset) {
        String text = tagField.getText().trim();
        if (!text.startsWith("#")) {
            text = "#" + text;
        }
        if (Identifier.tryParse(text.substring(1)) == null || text.length() < 3) {
            return;
        }
        if (preset.find(text) == null) {
            preset.blocks.add(new BlockRule(text, -1, EspCategory.ORE.name(), true, true));
            PresetManager.changed(preset);
        }
        onlyPreset = true;
        screen.rebuild();
    }

    @Override
    public void render(XrayScreen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        var text = screen.font();
        int nameRight = x + width - 22 - 4 - 22 - 4 - 22 - 4 - 92 - 6;
        if (rows.isEmpty()) {
            context.drawTextWithShadow(text, Text.translatable("minexray.blocks.empty").formatted(Formatting.GRAY), x, listTop + 4, 0xFFFFFF);
        }
        for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows); i++) {
            int y = listTop + (i - scroll) * ROW;
            Object row = rows.get(i);
            if (row instanceof Block block) {
                context.drawItem(new ItemStack(block.asItem()), x, y + 2);
                String name = block.getName().getString();
                String id = Registries.BLOCK.getId(block).toString();
                if (BlockFilter.deepslateTwin(Registries.BLOCK.getId(block)) != null
                        && Registries.BLOCK.containsId(BlockFilter.deepslateTwin(Registries.BLOCK.getId(block)))) {
                    name += " " + com.minemenu.core.Tr.get("minexray.blocks.with_deepslate", "(+ deepslate)");
                }
                context.drawTextWithShadow(text, text.trimToWidth(name, nameRight - x - 20), x + 20, y + 2, 0xFFFFFF);
                context.drawTextWithShadow(text, Text.literal(text.trimToWidth(id, nameRight - x - 20)).formatted(Formatting.DARK_GRAY), x + 20, y + 11, 0xFFFFFF);
            } else if (row instanceof BlockRule rule) {
                context.drawTextWithShadow(text, Text.literal("# ").formatted(Formatting.AQUA), x + 4, y + 6, 0xFFFFFF);
                context.drawTextWithShadow(text, text.trimToWidth(rule.id, nameRight - x - 20), x + 20, y + 2, 0xFFFFFF);
                context.drawTextWithShadow(text, Text.translatable("minexray.blocks.tag_count", BlockFilter.resolve(rule.id).size())
                        .formatted(Formatting.DARK_GRAY), x + 20, y + 11, 0xFFFFFF);
            }
        }
        if (rows.size() > visibleRows) {
            context.drawTextWithShadow(text, Text.literal((scroll + 1) + "-" + Math.min(rows.size(), scroll + visibleRows) + " / " + rows.size())
                    .formatted(Formatting.DARK_GRAY), x + width - 70, listTop - 11, 0xFFFFFF);
        }
    }

    @Override
    public boolean scrolled(XrayScreen screen, double amount) {
        int before = scroll;
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows), scroll - (int) Math.signum(amount) * 3));
        if (scroll != before) {
            screen.rebuild();
        }
        return true;
    }
}
