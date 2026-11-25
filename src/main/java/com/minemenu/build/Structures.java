package com.minemenu.build;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Instant Structures: small builds made in code (house, tower, castle, bridge, farm, Nether portal), placed on the
 * block you look at, facing away from you, with Undo; and real vanilla structures through the game's own
 * /place command (those can't be undone: the game decides their size).
 */
public final class Structures {
    public enum Built { HOUSE, TOWER, CASTLE, BRIDGE, FARM, NETHER_PORTAL }

    /** Vanilla structures for /place structure: id and name. */
    public static final String[][] VANILLA = {
            {"minecraft:village_plains", "Plains Village"}, {"minecraft:village_desert", "Desert Village"},
            {"minecraft:desert_pyramid", "Desert Temple"}, {"minecraft:jungle_pyramid", "Jungle Temple"},
            {"minecraft:pillager_outpost", "Pillager Outpost"}, {"minecraft:igloo", "Igloo"}, {"minecraft:swamp_hut", "Witch Hut"},
            {"minecraft:shipwreck", "Shipwreck"}, {"minecraft:ruined_portal", "Ruined Portal"}, {"minecraft:trail_ruins", "Trail Ruins"}};

    private Structures() {
    }

    private static BlockPos target(ServerPlayerEntity player) {
        HitResult hit = player.raycast(64, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.literal("Look at the ground where it should go").formatted(Formatting.YELLOW), true);
            return null;
        }
        return blockHit.getBlockPos().up();
    }

    public static void placeVanilla(ServerPlayerEntity player, String id) {
        BlockPos at = target(player);
        if (at == null) {
            return;
        }
        VanillaStructures.place(player, id, at); // remembers the area first, so Undo works
    }

    public static void place(ServerPlayerEntity player, Built kind) {
        BlockPos at = target(player);
        if (at == null) {
            return;
        }
        Direction facing = player.getHorizontalFacing();
        Builder b = new Builder(at, facing);
        switch (kind) {
            case HOUSE -> house(b);
            case TOWER -> tower(b);
            case CASTLE -> castle(b);
            case BRIDGE -> bridge(b);
            case FARM -> farm(b);
            case NETHER_PORTAL -> portal(b);
        }
        BlockPlacer.submit(player.getServerWorld(), b.changes, kind.name().charAt(0) + kind.name().substring(1).toLowerCase().replace('_', ' '), player, true);
    }

    /** Coordinates relative to the anchor: x = right, y = up, z = forward (away from the player). */
    private static final class Builder {
        final BlockPos origin;
        final Direction forward;
        final Direction right;
        final List<BlockChange> changes = new ArrayList<>();

        Builder(BlockPos origin, Direction forward) {
            this.origin = origin;
            this.forward = forward;
            this.right = forward.rotateYClockwise();
        }

        BlockPos at(int x, int y, int z) {
            return origin.offset(right, x).up(y).offset(forward, z);
        }

        void set(int x, int y, int z, BlockState state) {
            changes.add(new BlockChange(at(x, y, z), state, null));
        }

        void box(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        set(x, y, z, state);
                    }
                }
            }
        }

        /** Walls only (hollow). */
        void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) {
                        if (x == x1 || x == x2 || z == z1 || z == z2) {
                            set(x, y, z, state);
                        }
                    }
                }
            }
        }
    }

    private static void house(Builder b) {
        b.box(-3, 0, 1, 3, 4, 7, Blocks.AIR.getDefaultState());
        b.box(-3, -1, 1, 3, -1, 7, Blocks.COBBLESTONE.getDefaultState());
        b.walls(-3, 0, 1, 3, 3, 7, Blocks.OAK_PLANKS.getDefaultState());
        for (int y = 0; y <= 3; y++) {
            b.set(-3, y, 1, Blocks.OAK_LOG.getDefaultState());
            b.set(3, y, 1, Blocks.OAK_LOG.getDefaultState());
            b.set(-3, y, 7, Blocks.OAK_LOG.getDefaultState());
            b.set(3, y, 7, Blocks.OAK_LOG.getDefaultState());
        }
        b.box(-3, 4, 1, 3, 4, 7, Blocks.SPRUCE_PLANKS.getDefaultState());
        b.set(-3, 2, 4, Blocks.GLASS_PANE.getDefaultState());
        b.set(3, 2, 4, Blocks.GLASS_PANE.getDefaultState());
        b.set(0, 2, 7, Blocks.GLASS_PANE.getDefaultState());
        BlockState door = Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, b.forward.getOpposite());
        b.set(0, 0, 1, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(0, 1, 1, door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        b.set(-2, 0, 6, Blocks.CRAFTING_TABLE.getDefaultState());
        b.set(2, 0, 6, Blocks.FURNACE.getDefaultState());
        b.set(2, 0, 2, Blocks.CHEST.getDefaultState());
        b.set(0, 3, 4, Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
    }

    private static void tower(Builder b) {
        b.box(-2, 0, 1, 2, 16, 5, Blocks.AIR.getDefaultState());
        b.walls(-2, 0, 1, 2, 14, 5, Blocks.STONE_BRICKS.getDefaultState());
        b.box(-2, -1, 1, 2, -1, 5, Blocks.STONE_BRICKS.getDefaultState());
        b.box(-2, 15, 1, 2, 15, 5, Blocks.STONE_BRICKS.getDefaultState());
        for (int x = -2; x <= 2; x += 2) {
            b.set(x, 16, 1, Blocks.STONE_BRICK_WALL.getDefaultState());
            b.set(x, 16, 5, Blocks.STONE_BRICK_WALL.getDefaultState());
        }
        b.set(0, 0, 1, Blocks.AIR.getDefaultState());
        b.set(0, 1, 1, Blocks.AIR.getDefaultState());
        for (int y = 0; y <= 14; y++) {
            b.set(0, y, 4, Blocks.LADDER.getDefaultState().with(net.minecraft.block.LadderBlock.FACING, b.forward.getOpposite()));
        }
        b.set(0, 15, 4, Blocks.AIR.getDefaultState());
    }

    private static void castle(Builder b) {
        int s = 10;
        b.box(-s, -1, 1, s, -1, 1 + 2 * s, Blocks.STONE_BRICKS.getDefaultState());
        b.walls(-s, 0, 1, s, 6, 1 + 2 * s, Blocks.STONE_BRICKS.getDefaultState());
        for (int x = -s; x <= s; x += 2) {
            b.set(x, 7, 1, Blocks.STONE_BRICKS.getDefaultState());
            b.set(x, 7, 1 + 2 * s, Blocks.STONE_BRICKS.getDefaultState());
        }
        for (int z = 1; z <= 1 + 2 * s; z += 2) {
            b.set(-s, 7, z, Blocks.STONE_BRICKS.getDefaultState());
            b.set(s, 7, z, Blocks.STONE_BRICKS.getDefaultState());
        }
        int[][] corners = {{-s, 1}, {s, 1}, {-s, 1 + 2 * s}, {s, 1 + 2 * s}};
        for (int[] c : corners) {
            b.box(c[0] - 1, 0, c[1] - 1, c[0] + 1, 10, c[1] + 1, Blocks.COBBLESTONE.getDefaultState());
            b.set(c[0], 11, c[1], Blocks.LANTERN.getDefaultState());
        }
        b.box(-1, 0, 1, 1, 3, 1, Blocks.AIR.getDefaultState());
        b.box(-1, 4, 1, 1, 4, 1, Blocks.OAK_PLANKS.getDefaultState());
    }

    private static void bridge(Builder b) {
        for (int z = 1; z <= 24; z++) {
            b.box(-1, -1, z, 1, -1, z, Blocks.SPRUCE_PLANKS.getDefaultState());
            b.set(-2, 0, z, Blocks.SPRUCE_FENCE.getDefaultState());
            b.set(2, 0, z, Blocks.SPRUCE_FENCE.getDefaultState());
            b.box(-1, 0, z, 1, 3, z, Blocks.AIR.getDefaultState());
            if (z % 6 == 0) {
                b.set(-2, 1, z, Blocks.LANTERN.getDefaultState());
                b.set(2, 1, z, Blocks.LANTERN.getDefaultState());
            }
        }
    }

    private static void farm(Builder b) {
        b.box(-4, 0, 1, 4, 2, 9, Blocks.AIR.getDefaultState());
        for (int x = -4; x <= 4; x++) {
            for (int z = 1; z <= 9; z++) {
                boolean center = x == 0 && z == 5;
                b.set(x, -1, z, center ? Blocks.WATER.getDefaultState() : Blocks.FARMLAND.getDefaultState());
                if (!center) {
                    b.set(x, 0, z, Blocks.WHEAT.getDefaultState());
                }
            }
        }
        b.walls(-5, 0, 0, 5, 0, 10, Blocks.OAK_FENCE.getDefaultState());
        b.set(0, 0, 0, Blocks.OAK_FENCE_GATE.getDefaultState().with(net.minecraft.block.FenceGateBlock.FACING, b.forward));
    }

    private static void portal(Builder b) {
        b.box(-2, 0, 2, 1, 4, 2, Blocks.OBSIDIAN.getDefaultState());
        var axis = b.right.getAxis();
        b.box(-1, 1, 2, 0, 3, 2, Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, axis));
    }
}
