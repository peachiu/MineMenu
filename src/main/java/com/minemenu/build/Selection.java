package com.minemenu.build;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** The Build selection: two corners (set by looking with the Corner buttons, or with the Selection Wand). */
public final class Selection {
    private static volatile BlockPos corner1;
    private static volatile BlockPos corner2;
    private static volatile String dimension;
    /** Biggest selection allowed for one operation. */
    public static final int MAX_BLOCKS = 4_000_000;

    private Selection() {
    }

    public static void set(int corner, BlockPos pos, ServerPlayerEntity player) {
        String dim = player.getServerWorld().getRegistryKey().getValue().toString();
        if (!dim.equals(dimension)) {
            corner1 = corner2 = null;
        }
        dimension = dim;
        if (corner == 1) {
            corner1 = pos.toImmutable();
        } else {
            corner2 = pos.toImmutable();
        }
        BlockBox box = box();
        player.sendMessage(Text.literal("Corner " + corner + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + (box != null ? "  (" + box.getBlockCountX() + "x" + box.getBlockCountY() + "x" + box.getBlockCountZ() + " = " + volume(box) + " blocks)" : ""))
                .formatted(Formatting.AQUA), true);
    }

    /** Corner from the block the player looks at (up to 128 blocks). */
    public static void setFromLook(int corner, ServerPlayerEntity player) {
        HitResult hit = player.raycast(128, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.literal("Look at a block").formatted(Formatting.YELLOW), true);
            return;
        }
        set(corner, blockHit.getBlockPos(), player);
    }

    public static @Nullable BlockBox box() {
        BlockPos a = corner1;
        BlockPos b = corner2;
        return a == null || b == null ? null : BlockBox.create(a, b);
    }

    public static long volume(BlockBox box) {
        return (long) box.getBlockCountX() * box.getBlockCountY() * box.getBlockCountZ();
    }

    public static @Nullable String dimension() {
        return dimension;
    }

    public static @Nullable BlockPos corner(int i) {
        return i == 1 ? corner1 : corner2;
    }

    public static void clear() {
        corner1 = corner2 = null;
        dimension = null;
    }

    /** The selection, or null after telling the player what's missing / too big. */
    public static @Nullable BlockBox require(ServerPlayerEntity player) {
        BlockBox box = box();
        if (box == null || !player.getServerWorld().getRegistryKey().getValue().toString().equals(dimension)) {
            player.sendMessage(Text.literal("Set both corners first (Corner 1 / Corner 2, or the Selection Wand)").formatted(Formatting.YELLOW), true);
            return null;
        }
        if (volume(box) > MAX_BLOCKS) {
            player.sendMessage(Text.literal("The selection is too big (" + volume(box) + " blocks, max " + MAX_BLOCKS + ")").formatted(Formatting.RED), true);
            return null;
        }
        return box;
    }
}
