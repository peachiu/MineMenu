package com.minemenu.build;

import com.minemenu.Feature;
import com.minemenu.core.CheatConfig;
import com.minemenu.core.FeatureSettings;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Terrain Brush: right-click with a golden shovel to sculpt where you look (up to 64 blocks): flatten, raise,
 * lower, smooth, paint the surface or replace blocks with the block in your off hand, or drain water and lava.
 * Every stroke can be undone.
 */
public final class TerrainBrush {
    public enum Mode { FLATTEN, RAISE, LOWER, SMOOTH, PAINT, REPLACE, DRAIN }

    private TerrainBrush() {
    }

    public static Mode mode() {
        try {
            return Mode.valueOf(CheatConfig.get().text("brush.mode", "FLATTEN"));
        } catch (IllegalArgumentException e) {
            return Mode.FLATTEN;
        }
    }

    public static void setMode(Mode mode) {
        CheatConfig.get().setText("brush.mode", mode.name());
    }

    /** Right-click with the brush tool. */
    public static void use(ServerPlayerEntity player) {
        HitResult hit = player.raycast(64, 1.0f, mode() == Mode.DRAIN);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        BlockPos center = blockHit.getBlockPos();
        int r = (int) FeatureSettings.get(Feature.TERRAIN_BRUSH, "radius");
        BlockState paint = player.getOffHandStack().getItem() instanceof BlockItem block ? block.getBlock().getDefaultState() : null;
        List<BlockChange> changes = new ArrayList<>();
        Mode mode = mode();
        switch (mode) {
            case FLATTEN, RAISE, LOWER, SMOOTH -> heights(world, center, r, mode, changes);
            case PAINT -> {
                if (paint == null) {
                    player.sendMessage(Text.translatable("minemenu.text.hold_the_block_to_paint_with").formatted(Formatting.YELLOW), true);
                    return;
                }
                columns(center, r, (x, z) -> {
                    int top = surface(world, x, center.getY(), z, r);
                    if (top != Integer.MIN_VALUE) {
                        changes.add(new BlockChange(new BlockPos(x, top, z), paint, null));
                    }
                });
            }
            case REPLACE -> {
                if (paint == null) {
                    player.sendMessage(Text.translatable("minemenu.text.hold_the_new_block_in_your").formatted(Formatting.YELLOW), true);
                    return;
                }
                BlockState from = world.getBlockState(center);
                sphere(center, r, pos -> {
                    if (world.getBlockState(pos).getBlock() == from.getBlock()) {
                        changes.add(new BlockChange(pos, paint, null));
                    }
                });
            }
            case DRAIN -> sphere(center, r, pos -> {
                BlockState state = world.getBlockState(pos);
                if (state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED)) {
                    changes.add(new BlockChange(pos, state.with(Properties.WATERLOGGED, false), null));
                } else if (!state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.block.FluidBlock) {
                    changes.add(new BlockChange(pos, Blocks.AIR.getDefaultState(), null));
                }
            });
        }
        BlockPlacer.submit(world, changes, "minemenu.job.brush", player, true);
    }

    private interface Column {
        void at(int x, int z);
    }

    private static void columns(BlockPos center, int r, Column column) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= r * r) {
                    column.at(center.getX() + x, center.getZ() + z);
                }
            }
        }
    }

    private static void sphere(BlockPos center, int r, java.util.function.Consumer<BlockPos> action) {
        for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
            if (pos.getSquaredDistance(center) <= r * r) {
                action.accept(pos.toImmutable());
            }
        }
    }

    /** Highest solid block in this column near {@code y}, or MIN_VALUE. */
    private static int surface(ServerWorld world, int x, int y, int z, int r) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int yy = y + r * 2; yy >= y - r * 2; yy--) {
            pos.set(x, yy, z);
            if (!world.getBlockState(pos).isAir() && world.getBlockState(pos).getFluidState().isEmpty()) {
                return yy;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Flatten, raise, lower and smooth all work on column heights. */
    private static void heights(ServerWorld world, BlockPos center, int r, Mode mode, List<BlockChange> changes) {
        Map<Long, Integer> tops = new HashMap<>();
        for (int x = -r - 1; x <= r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                tops.put(key(center.getX() + x, center.getZ() + z), surface(world, center.getX() + x, center.getY(), center.getZ() + z, r));
            }
        }
        columns(center, r, (x, z) -> {
            int top = tops.get(key(x, z));
            if (top == Integer.MIN_VALUE) {
                return;
            }
            int target = switch (mode) {
                case FLATTEN -> center.getY();
                case RAISE -> top + 1;
                case LOWER -> top - 1;
                default -> {
                    int sum = 0;
                    int count = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Integer t = tops.get(key(x + dx, z + dz));
                            if (t != null && t != Integer.MIN_VALUE) {
                                sum += t;
                                count++;
                            }
                        }
                    }
                    yield count == 0 ? top : Math.round(sum / (float) count);
                }
            };
            BlockState surfaceBlock = world.getBlockState(new BlockPos(x, top, z));
            BlockState fill = surfaceBlock.isOf(Blocks.GRASS_BLOCK) ? Blocks.DIRT.getDefaultState() : surfaceBlock;
            for (int y = Math.min(top, target) + 1; y <= Math.max(top, target); y++) {
                // Going up: fill with the ground block; going down: dig out.
                changes.add(new BlockChange(new BlockPos(x, y, z), target > top ? fill : Blocks.AIR.getDefaultState(), null));
            }
            if (target < top) {
                changes.add(new BlockChange(new BlockPos(x, target, z), surfaceBlock, null));
            } else if (target > top) {
                changes.add(new BlockChange(new BlockPos(x, target, z), surfaceBlock, null));
                changes.add(new BlockChange(new BlockPos(x, top, z), fill, null));
            }
        });
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
