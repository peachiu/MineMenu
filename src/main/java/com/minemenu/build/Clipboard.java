package com.minemenu.build;

import com.minemenu.mixin.StructureTemplateAccessor;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Copy / Paste: the copy is a vanilla StructureTemplate (the same thing structure blocks use), so it can also be
 * saved and loaded as a .nbt schematic. Paste shows a preview box where you look; Confirm places it, a few
 * thousand blocks per tick, with Undo.
 */
public final class Clipboard {
    private static volatile StructureTemplate template;
    private static volatile BlockRotation rotation = BlockRotation.NONE;
    private static volatile BlockMirror mirror = BlockMirror.NONE;
    /** Preview on/off and the block it's anchored to (set from the client's crosshair every tick). */
    public static volatile boolean previewing;
    public static volatile BlockPos anchor;

    private Clipboard() {
    }

    public static @Nullable StructureTemplate template() {
        return template;
    }

    public static void set(StructureTemplate copy) {
        template = copy;
        rotation = BlockRotation.NONE;
        mirror = BlockMirror.NONE;
    }

    public static void clear() {
        template = null;
        previewing = false;
        anchor = null;
    }

    public static void copy(ServerPlayerEntity player) {
        BlockBox box = Selection.require(player);
        if (box == null) {
            return;
        }
        StructureTemplate copy = new StructureTemplate();
        copy.saveFromWorld(player.getServerWorld(), new BlockPos(box.getMinX(), box.getMinY(), box.getMinZ()),
                new Vec3i(box.getBlockCountX(), box.getBlockCountY(), box.getBlockCountZ()), false, Blocks.STRUCTURE_VOID);
        set(copy);
        player.sendMessage(Text.literal("Copied " + Selection.volume(box) + " blocks. Paste (preview) shows where it goes.").formatted(Formatting.GREEN), true);
    }

    public static void rotate(ServerPlayerEntity player) {
        rotation = rotation.rotate(BlockRotation.CLOCKWISE_90);
        player.sendMessage(Text.literal("Rotation: " + rotationName()).formatted(Formatting.AQUA), true);
    }

    public static void mirror(ServerPlayerEntity player) {
        mirror = mirror == BlockMirror.NONE ? BlockMirror.FRONT_BACK : BlockMirror.NONE;
        player.sendMessage(Text.literal("Mirror: " + (mirror == BlockMirror.NONE ? "off" : "on")).formatted(Formatting.AQUA), true);
    }

    private static String rotationName() {
        return switch (rotation) {
            case NONE -> "0°";
            case CLOCKWISE_90 -> "90°";
            case CLOCKWISE_180 -> "180°";
            case COUNTERCLOCKWISE_90 -> "270°";
        };
    }

    private static StructurePlacementData placement() {
        return new StructurePlacementData().setRotation(rotation).setMirror(mirror);
    }

    /** Where the copy would go with its lowest corner on {@code at}: the box, for the preview. */
    public static @Nullable BlockBox previewBox(BlockPos at) {
        StructureTemplate t = template;
        if (t == null || at == null) {
            return null;
        }
        BlockBox raw = t.calculateBoundingBox(placement(), BlockPos.ORIGIN);
        return raw.offset(at.getX() - raw.getMinX(), at.getY() - raw.getMinY(), at.getZ() - raw.getMinZ());
    }

    public static void startPreview(ServerPlayerEntity player) {
        if (template == null) {
            player.sendMessage(Text.literal("Copy something first (or load a schematic)").formatted(Formatting.YELLOW), true);
            return;
        }
        previewing = true;
        player.sendMessage(Text.literal("Look where it should go, then Confirm Paste. Rotate / Mirror work while previewing.").formatted(Formatting.AQUA), true);
    }

    public static void paste(ServerPlayerEntity player) {
        StructureTemplate t = template;
        BlockPos at = anchor;
        if (t == null || !previewing || at == null) {
            player.sendMessage(Text.literal("Start the preview first (Paste), and look at a block").formatted(Formatting.YELLOW), true);
            return;
        }
        previewing = false;
        BlockBox raw = t.calculateBoundingBox(placement(), BlockPos.ORIGIN);
        BlockPos offset = at.add(-raw.getMinX(), -raw.getMinY(), -raw.getMinZ());
        StructurePlacementData data = placement();
        List<BlockChange> changes = new ArrayList<>();
        List<StructureTemplate.PalettedBlockInfoList> lists = ((StructureTemplateAccessor) t).minemenu$getBlockInfoLists();
        if (lists.isEmpty()) {
            return;
        }
        for (StructureTemplate.StructureBlockInfo info : lists.get(0).getAll()) {
            BlockPos pos = StructureTemplate.transform(data, info.pos()).add(offset);
            changes.add(new BlockChange(pos, info.state().mirror(mirror).rotate(rotation), info.nbt()));
        }
        ServerWorld world = player.getServerWorld();
        BlockPlacer.submit(world, changes, "Paste", player, true);
    }
}
