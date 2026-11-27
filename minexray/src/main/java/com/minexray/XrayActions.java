package com.minexray;

import com.minexray.render.OverlayRenderer;
import com.minexray.scan.VeinMerger;
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
import java.util.HashSet;
import java.util.Set;

/**
 * Things you can do with a vein (mark as waypoint, show for 30 s, teleport next to it, mine it whole), adding
 * blocks to the list. (MineXray's lines in MineMenu's Command Palette are added by MineMeBoostXray.)
 */
public final class XrayActions {
    private static final int MAX_VEIN_MINE = 128;

    private XrayActions() {
    }

    /** The name a vein shows: "Diamond Ore". */
    public static String name(VeinMerger.Vein vein) {
        return vein.block().getName().getString();
    }

    // ------------------------------------------------------------------
    // Vein actions
    // ------------------------------------------------------------------

    /** Waypoints come from MineMenu; on its own MineXray has none (the button is greyed out). */
    public static boolean waypointsAvailable() {
        return XrayHost.waypoints != null;
    }

    public static void waypoint(VeinMerger.Vein vein) {
        if (XrayHost.waypoints == null) {
            return;
        }
        String name = name(vein) + " x" + vein.count();
        BlockPos pos = vein.nearest();
        XrayHost.onServer(player -> {
            int slot = XrayHost.waypoints.save(name, player.getServerWorld().getRegistryKey().getValue().toString(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            player.sendMessage(slot >= 0 ? Text.translatable("minexray.scanner.waypoint_set", name, slot + 1).formatted(Formatting.GREEN)
                    : Text.translatable("minexray.scanner.waypoints_full").formatted(Formatting.YELLOW), true);
        });
    }

    public static void show(VeinMerger.Vein vein) {
        OverlayRenderer.highlight(vein.nearest(), vein.color(), 30_000);
    }

    /** Singleplayer only: teleports you to the nearest safe air next to the vein. */
    public static void teleport(VeinMerger.Vein vein) {
        BlockPos target = vein.nearest();
        XrayHost.onServer(player -> {
            BlockPos spot = safeSpot(player.getServerWorld(), target);
            if (spot == null) {
                player.sendMessage(Text.translatable("minexray.scanner.no_safe_spot").formatted(Formatting.YELLOW), true);
                return;
            }
            player.requestTeleport(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            player.sendMessage(Text.translatable("minexray.scanner.teleported").formatted(Formatting.GREEN), true);
        });
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
        XrayHost.onServer(player -> {
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
        });
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
