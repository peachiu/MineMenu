package com.minexray;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * The active preset turned into lookups by block state id, so the scanner and the chunk renderer ask "is this one
 * shown?" with a bit test instead of a map lookup. Immutable: a change builds a new one and swaps it in, which
 * the background scanner and the render thread read without locks.
 */
public final class BlockFilter {
    private static volatile BlockFilter current = new BlockFilter();
    private static int versions;

    /** Highlighted by an ESP mode (or a candidate for it: modes are checked when drawing). */
    final BitSet scan;
    /** Kept in Classic X-Ray. */
    final BitSet visible;
    /** 0xRRGGBB per state id. */
    final int[] color;
    /** {@link EspCategory} ordinal per state id. */
    final byte[] category;
    /** Block raw id that a state counts as for veins (deepslate ores count as the normal ore). */
    final int[] group;
    public final int version;
    /** True if the ESP part changed since the previous filter (else only colours/Classic changed). */
    public final boolean scanChanged;

    private BlockFilter() {
        scan = new BitSet();
        visible = new BitSet();
        color = new int[0];
        category = new byte[0];
        group = new int[0];
        version = 0;
        scanChanged = true;
    }

    private BlockFilter(BitSet scan, BitSet visible, int[] color, byte[] category, int[] group, int version, boolean scanChanged) {
        this.scan = scan;
        this.visible = visible;
        this.color = color;
        this.category = category;
        this.group = group;
        this.version = version;
        this.scanChanged = scanChanged;
    }

    public static BlockFilter get() {
        return current;
    }

    public static int raw(BlockState state) {
        return Block.getRawIdFromState(state);
    }

    public boolean isScanned(BlockState state) {
        return scan.get(raw(state));
    }

    public boolean isScanned(int raw) {
        return scan.get(raw);
    }

    /** Classic X-Ray: drawn (true) or hidden. */
    public boolean isVisible(BlockState state) {
        return visible.get(raw(state));
    }

    public int color(int raw) {
        return raw < color.length ? color[raw] : 0xFFFFFF;
    }

    public EspCategory category(int raw) {
        return raw < category.length ? EspCategory.values()[category[raw]] : EspCategory.ORE;
    }

    public int group(int raw) {
        return raw < group.length ? group[raw] : -1;
    }

    public boolean anyScanned() {
        return !scan.isEmpty();
    }

    /** Builds the filter again from the active preset and the Block ESP categories. */
    public static synchronized void rebuild() {
        XrayConfig config = XrayConfig.get();
        int states = Block.STATE_IDS.size();
        BitSet scan = new BitSet(states);
        BitSet visible = new BitSet(states);
        int[] color = new int[states];
        byte[] category = new byte[states];
        int[] group = new int[states];
        java.util.Arrays.fill(group, -1);
        for (BlockRule rule : PresetManager.active().blocks) {
            EspCategory cat = EspCategory.parse(rule.category);
            for (Block block : resolve(rule.id)) {
                int ruleColor = rule.color >= 0 ? rule.color : cat.oreLayer() ? block.getDefaultMapColor().color : config.categoryColor(cat);
                int groupId = Registries.BLOCK.getRawId(groupOf(block));
                for (BlockState state : block.getStateManager().getStates()) {
                    int raw = raw(state);
                    if (rule.visible) {
                        visible.set(raw);
                    }
                    if (rule.esp && config.categoryOn(cat)) {
                        scan.set(raw);
                        color[raw] = ruleColor & 0xFFFFFF;
                        category[raw] = (byte) cat.ordinal();
                        group[raw] = groupId;
                    }
                }
            }
        }
        // Block ESP: each switched-on category brings its usual blocks even if the preset doesn't list them.
        for (EspCategory cat : EspCategory.values()) {
            if (cat.oreLayer() || !config.categoryOn(cat)) {
                continue;
            }
            for (String id : cat.defaultBlocks) {
                for (Block block : resolve(id)) {
                    for (BlockState state : block.getStateManager().getStates()) {
                        int raw = raw(state);
                        if (!scan.get(raw)) {
                            scan.set(raw);
                            color[raw] = config.categoryColor(cat) & 0xFFFFFF;
                            category[raw] = (byte) cat.ordinal();
                            group[raw] = Registries.BLOCK.getRawId(block);
                        }
                    }
                }
            }
        }
        BlockFilter old = current;
        boolean scanChanged = !scan.equals(old.scan) || !java.util.Arrays.equals(category, old.category);
        boolean visibleChanged = !visible.equals(old.visible);
        current = new BlockFilter(scan, visible, color, category, group, ++versions, scanChanged);
        MineXrayClient.filterChanged(current, visibleChanged);
    }

    /** Blocks a rule id stands for: a tag's blocks, or the block and its deepslate twin. */
    public static List<Block> resolve(String id) {
        List<Block> list = new ArrayList<>();
        if (id == null || id.isBlank()) {
            return list;
        }
        if (id.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(id.substring(1));
            if (tagId != null) {
                for (RegistryEntry<Block> entry : Registries.BLOCK.iterateEntries(TagKey.of(RegistryKeys.BLOCK, tagId))) {
                    list.add(entry.value());
                }
            }
            return list;
        }
        Identifier blockId = Identifier.tryParse(id);
        if (blockId == null || !Registries.BLOCK.containsId(blockId)) {
            return list;
        }
        list.add(Registries.BLOCK.get(blockId));
        Identifier twin = deepslateTwin(blockId);
        if (twin != null && Registries.BLOCK.containsId(twin)) {
            list.add(Registries.BLOCK.get(twin));
        }
        return list;
    }

    /** diamond_ore <-> deepslate_diamond_ore (any namespace, so modded ores pair up too). */
    public static Identifier deepslateTwin(Identifier id) {
        String path = id.getPath();
        if (!path.endsWith("_ore")) {
            return null;
        }
        return path.startsWith("deepslate_") ? Identifier.of(id.getNamespace(), path.substring("deepslate_".length()))
                : Identifier.of(id.getNamespace(), "deepslate_" + path);
    }

    /** The block a vein of {@code block} is named after (the normal ore for deepslate ones). */
    public static Block groupOf(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        if (id.getPath().startsWith("deepslate_") && id.getPath().endsWith("_ore")) {
            Identifier base = deepslateTwin(id);
            if (base != null && Registries.BLOCK.containsId(base)) {
                return Registries.BLOCK.get(base);
            }
        }
        return block;
    }
}
