package com.minexray;

import com.minemenu.Waypoints;
import com.minemenu.ui.MenuEntries;
import com.minemenu.ui.MenuEntry;
import com.minexray.render.OverlayRenderer;
import com.minexray.scan.VeinMerger;
import com.minexray.screen.XrayScreen;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Things you can do with a vein (mark as waypoint, show for 30 s, teleport next to it, mine it whole), adding
 * blocks to the list, and MineXray's lines in the Command Palette ("xray diamonds", "xray ore esp"...).
 */
public final class XrayActions {
    private static final int MAX_VEIN_MINE = 128;

    private XrayActions() {
    }

    static void registerEntries() {
        Supplier<List<MenuEntry>> before = MenuEntries.extra;
        MenuEntries.extra = () -> {
            List<MenuEntry> list = new ArrayList<>(before.get());
            list.addAll(entries());
            return list;
        };
    }

    private static List<MenuEntry> entries() {
        List<MenuEntry> list = new ArrayList<>();
        for (XrayMode mode : XrayMode.values()) {
            list.add(new MenuEntry("xray:mode:" + mode.id, "Xray " + mode.displayName(), mode.description(), null, MenuEntry.Kind.HACK,
                    () -> XrayConfig.get().isOn(mode), (client, parent) -> XrayKeys.toggle(client, mode), false));
        }
        for (XrayPreset preset : PresetManager.all()) {
            list.add(new MenuEntry("xray:preset:" + preset.name, "Xray " + preset.name,
                    com.minemenu.core.Tr.get("minexray.entry.preset.desc", "Use this MineXray preset."), null, MenuEntry.Kind.PRESET,
                    () -> preset.name.equals(XrayConfig.get().activePreset), (client, parent) -> {
                        PresetManager.apply(preset.name, true);
                        XrayKeys.message(client, Text.translatable("minexray.preset.now", preset.name).formatted(Formatting.AQUA));
                    }, false));
        }
        list.add(new MenuEntry("xray:scan", com.minemenu.core.Tr.get("minexray.entry.scan", "Xray Scan now"),
                com.minemenu.core.Tr.get("minexray.entry.scan.desc", "List every vein around you."), null, MenuEntry.Kind.SCREEN, null,
                (client, parent) -> client.setScreen(new XrayScreen(parent, XrayScreen.Page.SCANNER, true)), false));
        list.add(new MenuEntry("xray:open", com.minemenu.core.Tr.get("minexray.entry.open", "Xray: open MineXray"),
                com.minemenu.core.Tr.get("minexray.entry.open.desc", "X-ray and ESP modes, block list, presets and scanner."), null,
                MenuEntry.Kind.SCREEN, null, (client, parent) -> client.setScreen(new XrayScreen(parent, XrayScreen.Page.OVERVIEW, false)), true));
        list.add(new MenuEntry("xray:mine_nearest", com.minemenu.core.Tr.get("minexray.entry.mine_nearest", "Xray Mine nearest vein"),
                com.minemenu.core.Tr.get("minexray.entry.mine_nearest.desc", "Mines the whole nearest highlighted vein (Vein Miner)."), null,
                MenuEntry.Kind.ACTION, null, (client, parent) -> {
                    List<VeinMerger.Vein> veins = VeinMerger.veins();
                    if (!veins.isEmpty()) {
                        mine(veins.get(0));
                    }
                }, false));
        return list;
    }

    /** The name a vein shows: "Diamond Ore". */
    public static String name(VeinMerger.Vein vein) {
        return vein.block().getName().getString();
    }

    // ------------------------------------------------------------------
    // Vein actions
    // ------------------------------------------------------------------

    public static void waypoint(VeinMerger.Vein vein) {
        String name = name(vein) + " x" + vein.count();
        BlockPos pos = vein.nearest();
        MenuEntries.onServer(player -> {
            int slot = Waypoints.addAt(name, player.getServerWorld().getRegistryKey().getValue().toString(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            player.sendMessage(slot >= 0 ? Text.translatable("minexray.scanner.waypoint_set", name, slot + 1).formatted(Formatting.GREEN)
                    : Text.translatable("minexray.scanner.waypoints_full").formatted(Formatting.YELLOW), true);
        }).accept(MinecraftClient.getInstance(), null);
    }

    public static void show(VeinMerger.Vein vein) {
        OverlayRenderer.highlight(vein.nearest(), vein.color(), 30_000);
    }

    /** Singleplayer only: teleports you to the nearest safe air next to the vein. */
    public static void teleport(VeinMerger.Vein vein) {
        BlockPos target = vein.nearest();
        MenuEntries.onServer(player -> {
            BlockPos spot = safeSpot(player.getServerWorld(), target);
            if (spot == null) {
                player.sendMessage(Text.translatable("minexray.scanner.no_safe_spot").formatted(Formatting.YELLOW), true);
                return;
            }
            player.requestTeleport(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            player.sendMessage(Text.translatable("minexray.scanner.teleported").formatted(Formatting.GREEN), true);
        }).accept(MinecraftClient.getInstance(), null);
    }

    /**
     * Two blocks of air (no lava near) with something solid under: the closest one within 6 blocks of
     * {@code target}, else the first one straight above it (ores are usually buried in solid stone).
     */
    public static BlockPos safeSpot(ServerWorld world, BlockPos target) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.iterate(target.add(-6, -6, -6), target.add(6, 6, 6))) {
            if (!safe(world, p)) {
                continue;
            }
            double d = p.getSquaredDistance(target);
            if (d < bestDistance) {
                bestDistance = d;
                best = p.toImmutable();
            }
        }
        if (best != null) {
            return best;
        }
        for (int y = target.getY() + 1; y < world.getTopY() - 2; y++) {
            BlockPos p = new BlockPos(target.getX(), y, target.getZ());
            if (safe(world, p)) {
                return p;
            }
        }
        return null;
    }

    private static boolean safe(ServerWorld world, BlockPos p) {
        return free(world, p) && free(world, p.up()) && world.getBlockState(p.down()).isSideSolidFullSquare(world, p.down(), Direction.UP)
                && !nearLava(world, p);
    }

    private static boolean free(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getCollisionShape(world, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean nearLava(ServerWorld world, BlockPos pos) {
        for (BlockPos p : BlockPos.iterate(pos.add(-1, -1, -1), pos.add(1, 2, 1))) {
            if (world.getBlockState(p).isOf(Blocks.LAVA)) {
                return true;
            }
        }
        return false;
    }

    /** Vein Miner: breaks every touching block of the vein (up to 128) with your tool; drops go to you. */
    public static void mine(VeinMerger.Vein vein) {
        BlockPos start = vein.nearest();
        MenuEntries.onServer(player -> {
            ServerWorld world = player.getServerWorld();
            Block block = BlockFilter.groupOf(world.getBlockState(start).getBlock());
            int mined = 0;
            Set<BlockPos> seen = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty() && mined < MAX_VEIN_MINE) {
                BlockPos pos = queue.poll();
                BlockState state = world.getBlockState(pos);
                if (state.isAir() || BlockFilter.groupOf(state.getBlock()) != block) {
                    continue;
                }
                BlockEntity blockEntity = world.getBlockEntity(pos);
                for (ItemStack drop : Block.getDroppedStacks(state, world, pos, blockEntity, player, player.getMainHandStack())) {
                    player.getInventory().offerOrDrop(drop);
                }
                state.onStacksDropped(world, pos, player.getMainHandStack(), true);
                world.breakBlock(pos, false, player);
                mined++;
                for (BlockPos next : BlockPos.iterate(pos.add(-1, -1, -1), pos.add(1, 1, 1))) {
                    if (seen.add(next.toImmutable())) {
                        queue.add(next.toImmutable());
                    }
                }
            }
            player.sendMessage(Text.translatable("minexray.scanner.mined", mined, block.getName()).formatted(Formatting.GREEN), true);
        }).accept(MinecraftClient.getInstance(), null);
    }

    // ------------------------------------------------------------------
    // Adding blocks to the list
    // ------------------------------------------------------------------

    /** Adds the block under the crosshair to the active preset (key: Add looked-at block). */
    public static void addLookedAt(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK || client.world == null) {
            XrayKeys.message(client, Text.translatable("minexray.blocks.look_at_block").formatted(Formatting.YELLOW));
            return;
        }
        add(client, client.world.getBlockState(blockHit.getBlockPos()).getBlock());
    }

    /** Adds the block of the item in your hand. */
    public static void addHeld(MinecraftClient client) {
        if (client.player == null || !(client.player.getMainHandStack().getItem() instanceof BlockItem item)) {
            XrayKeys.message(client, Text.translatable("minexray.blocks.hold_block").formatted(Formatting.YELLOW));
            return;
        }
        add(client, item.getBlock());
    }

    public static void add(MinecraftClient client, Block block) {
        String id = Registries.BLOCK.getId(BlockFilter.groupOf(block)).toString();
        XrayPreset preset = PresetManager.active();
        BlockRule rule = preset.find(id);
        if (rule == null) {
            preset.blocks.add(new BlockRule(id, -1, EspCategory.ORE.name(), true, true));
        } else {
            rule.esp = true;
            rule.visible = true;
        }
        PresetManager.changed(preset);
        XrayKeys.message(client, Text.translatable("minexray.blocks.added", block.getName(), preset.name).formatted(Formatting.GREEN));
    }
}
