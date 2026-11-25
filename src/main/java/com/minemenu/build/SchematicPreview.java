package com.minemenu.build;

import com.minemenu.mixin.StructureTemplateAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.EmptyBlockView;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * What the schematics browser shows for one file: its size, how many blocks, and two small pictures made from the
 * blocks' map colours: seen from above (darker = lower) and from the front. Read on the client from the .nbt file.
 */
public final class SchematicPreview {
    /** Pictures are at most this many cells wide/high (bigger schematics are sampled). */
    private static final int MAX_CELLS = 96;

    public final Vec3i size;
    public final int blocks;
    public final long fileBytes;
    public final FileTime modified;
    /** ARGB colours, row by row; 0 = empty. */
    public final int[] top;
    public final int topWidth;
    public final int topHeight;
    public final int[] front;
    public final int frontWidth;
    public final int frontHeight;

    private SchematicPreview(Vec3i size, int blocks, long fileBytes, FileTime modified, int[] top, int topWidth, int topHeight,
                             int[] front, int frontWidth, int frontHeight) {
        this.size = size;
        this.blocks = blocks;
        this.fileBytes = fileBytes;
        this.modified = modified;
        this.top = top;
        this.topWidth = topWidth;
        this.topHeight = topHeight;
        this.front = front;
        this.frontWidth = frontWidth;
        this.frontHeight = frontHeight;
    }

    public static SchematicPreview load(Path file) throws Exception {
        NbtCompound nbt = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
        StructureTemplate template = new StructureTemplate();
        template.readNbt(Registries.BLOCK.getReadOnlyWrapper(), nbt);
        Vec3i size = template.getSize();
        int sx = Math.max(1, size.getX()), sy = Math.max(1, size.getY()), sz = Math.max(1, size.getZ());
        int[] highest = new int[sx * sz];
        int[] highestColor = new int[sx * sz];
        int[] nearest = new int[sx * sy];
        int[] nearestColor = new int[sx * sy];
        java.util.Arrays.fill(highest, -1);
        java.util.Arrays.fill(nearest, Integer.MAX_VALUE);
        int blocks = 0;
        var lists = ((StructureTemplateAccessor) template).minemenu$getBlockInfoLists();
        if (!lists.isEmpty()) {
            for (StructureTemplate.StructureBlockInfo info : lists.get(0).getAll()) {
                BlockState state = info.state();
                if (state.isAir()) {
                    continue;
                }
                MapColor mapColor = state.getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
                if (mapColor == MapColor.CLEAR) {
                    continue;
                }
                blocks++;
                BlockPos p = info.pos();
                int x = p.getX(), y = p.getY(), z = p.getZ();
                if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
                    continue;
                }
                int column = z * sx + x;
                if (y > highest[column]) {
                    highest[column] = y;
                    highestColor[column] = mapColor.color;
                }
                int row = (sy - 1 - y) * sx + x; // top row of the picture = highest layer
                if (z < nearest[row]) {
                    nearest[row] = z;
                    nearestColor[row] = mapColor.color;
                }
            }
        }
        // Shading: lower (top view) or farther (front view) is darker, so shapes read at a glance.
        int tw = Math.min(sx, MAX_CELLS), th = Math.min(sz, MAX_CELLS);
        int[] top = new int[tw * th];
        for (int cz = 0; cz < th; cz++) {
            for (int cx = 0; cx < tw; cx++) {
                int column = (cz * sz / th) * sx + (cx * sx / tw);
                top[cz * tw + cx] = highest[column] < 0 ? 0 : shade(highestColor[column], 0.55 + 0.45 * (highest[column] + 1) / sy);
            }
        }
        int fw = Math.min(sx, MAX_CELLS), fh = Math.min(sy, MAX_CELLS);
        int[] front = new int[fw * fh];
        for (int cy = 0; cy < fh; cy++) {
            for (int cx = 0; cx < fw; cx++) {
                int row = (cy * sy / fh) * sx + (cx * sx / fw);
                front[cy * fw + cx] = nearest[row] == Integer.MAX_VALUE ? 0 : shade(nearestColor[row], 1.0 - 0.45 * nearest[row] / sz);
            }
        }
        return new SchematicPreview(size, blocks, Files.size(file), Files.getLastModifiedTime(file), top, tw, th, front, fw, fh);
    }

    private static int shade(int rgb, double factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor), g = (int) (((rgb >> 8) & 0xFF) * factor), b = (int) ((rgb & 0xFF) * factor);
        return 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }
}
