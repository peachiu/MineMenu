package com.minexray.scan;

import com.minexray.BlockFilter;
import com.minexray.EspCategory;
import com.minexray.XrayConfig;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a section's scan result into lines and faces, on the scanner thread. Touching blocks of the same kind
 * are drawn as one shape (a "fused vein"): only faces with no same-kind neighbour, and only the edges where the
 * shape actually bends, so a 2x2 diamond vein is one box instead of four. Neighbouring sections are looked up
 * in the cache so veins across section borders fuse too.
 */
public final class Mesher {
    /** Everything the geometry depends on besides the scan result. */
    public record Settings(int version, boolean ore, boolean block, XrayConfig.EspStyle style, int lineAlpha, int fillAlpha,
                           boolean caveDots, BlockFilter filter) {
    }

    private static final int[] DX = {0, 0, 0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};
    private static final int LAVA_COLOR = 0xFF7A00;
    private static final int WATER_COLOR = 0x3F76E4;
    private static final int CAVE_COLOR = 0x66CCFF;
    private static final int MAX_CAVE_MESH = 2500;

    private static volatile Settings settings;
    private static int builtVersion = -1;
    private static final Set<Long> queued = ConcurrentHashMap.newKeySet();

    private Mesher() {
    }

    public static void update(Settings now) {
        settings = now;
    }

    public static Settings settings() {
        return settings;
    }

    /** Client thread: settings changed since the meshes were built, so rebuild them all (in the background). */
    static void tick() {
        Settings s = settings;
        if (s != null && s.version() != builtVersion) {
            builtVersion = s.version();
            for (SectionData data : ScanCache.all()) {
                if (!data.isEmpty()) {
                    queue(data.key);
                }
            }
        }
    }

    static void queue(long key) {
        if (queued.add(key)) {
            SectionScanner.EXECUTOR.execute(() -> {
                queued.remove(key);
                SectionData data = ScanCache.get(key);
                if (data != null) {
                    try {
                        build(data);
                    } catch (Throwable t) {
                        com.minexray.MineXrayClient.backgroundError("mesher", t);
                    }
                }
            });
        }
    }

    /** A section's result changed: its neighbours' shapes may fuse with it differently now. */
    static void neighbourChanged(long key) {
        int x = ChunkSectionPos.unpackX(key);
        int y = ChunkSectionPos.unpackY(key);
        int z = ChunkSectionPos.unpackZ(key);
        for (int d = 0; d < 6; d++) {
            SectionData other = ScanCache.get(ChunkSectionPos.asLong(x + DX[d], y + DY[d], z + DZ[d]));
            if (other != null && !other.isEmpty() && other.mesh != null) {
                queue(other.key);
            }
        }
    }

    static void build(SectionData data) {
        Settings s = settings;
        if (s == null || data.isEmpty()) {
            return;
        }
        Builder b = new Builder(data, s);
        int[][] layers = new int[MeshData.LAYERS][];
        int espBlocks = 0;
        // Highlighted blocks.
        Ints lines = new Ints();
        Ints fill = new Ints();
        if (data.espIdx.length > 0 && (s.ore() || s.block())) {
            int[] grid = b.grid(0, 0, 0, Builder.ESP);
            for (int i = 0; i < data.espIdx.length; i++) {
                int idx = data.espIdx[i];
                if (grid[idx] != 0) {
                    espBlocks++;
                    int color = s.filter().color(data.espState[i]);
                    b.block(idx & 15, idx >> 8, (idx >> 4) & 15, grid[idx], Builder.ESP, color, s.style(), s.lineAlpha(), s.fillAlpha(), lines, fill);
                }
            }
        }
        layers[MeshData.ESP_LINES] = lines.toArray();
        layers[MeshData.ESP_FILL] = fill.toArray();
        // Lava and water.
        lines = new Ints();
        fill = new Ints();
        if (data.lava != null || data.water != null) {
            int[] grid = b.grid(0, 0, 0, Builder.FLUID);
            for (int idx = 0; idx < 4096; idx++) {
                if (grid[idx] != 0) {
                    b.block(idx & 15, idx >> 8, (idx >> 4) & 15, grid[idx], Builder.FLUID, grid[idx] == 1 ? LAVA_COLOR : WATER_COLOR,
                            XrayConfig.EspStyle.OUTLINE_FILL, Math.min(200, s.lineAlpha()), Math.max(30, s.fillAlpha() / 2), lines, fill);
                }
            }
        }
        layers[MeshData.FLUID_LINES] = lines.toArray();
        layers[MeshData.FLUID_FILL] = fill.toArray();
        // Cave air: dots, or the outline of the cave's shape.
        lines = new Ints();
        if (data.cave != null) {
            boolean dots = s.caveDots() || data.caveCount > MAX_CAVE_MESH;
            int[] grid = dots ? null : b.grid(0, 0, 0, Builder.CAVE);
            for (int idx = 0; idx < 4096; idx++) {
                if (!SectionData.bit(data.cave, idx)) {
                    continue;
                }
                int x = idx & 15, y = idx >> 8, z = (idx >> 4) & 15;
                if (dots) {
                    if (((x | y | z) & 1) == 0) {
                        Builder.dot(lines, x + 0.5f, y + 0.5f, z + 0.5f, 150 << 24 | CAVE_COLOR);
                    }
                } else {
                    b.block(x, y, z, grid[idx], Builder.CAVE, CAVE_COLOR, XrayConfig.EspStyle.OUTLINE, 110, 0, lines, null);
                }
            }
        }
        layers[MeshData.CAVE_LINES] = lines.toArray();
        MeshData old = data.mesh;
        MeshData mesh = new MeshData(layers, s.version(), espBlocks);
        data.mesh = mesh;
        ScanCache.meshReplaced(data, old, mesh);
    }

    /** Builds one section's shapes; knows what's in the neighbouring sections. */
    private static final class Builder {
        static final int ESP = 0;
        static final int FLUID = 1;
        static final int CAVE = 2;

        private final SectionData data;
        private final Settings settings;
        private final int sx;
        private final int sy;
        private final int sz;
        /** Grids of the 27 sections around (and including) this one, per kind, made when first needed. */
        private final int[][][] grids = new int[3][27][];

        Builder(SectionData data, Settings settings) {
            this.data = data;
            this.settings = settings;
            this.sx = ChunkSectionPos.unpackX(data.key);
            this.sy = ChunkSectionPos.unpackY(data.key);
            this.sz = ChunkSectionPos.unpackZ(data.key);
        }

        /** Kind id per local index (0 = nothing) for the section at offset (ox, oy, oz). */
        int[] grid(int ox, int oy, int oz, int kind) {
            int slot = (ox + 1) * 9 + (oy + 1) * 3 + (oz + 1);
            int[] grid = grids[kind][slot];
            if (grid == null) {
                SectionData d = ox == 0 && oy == 0 && oz == 0 ? data : ScanCache.get(ChunkSectionPos.asLong(sx + ox, sy + oy, sz + oz));
                grid = new int[4096];
                if (d != null) {
                    fill(d, kind, grid);
                }
                grids[kind][slot] = grid;
            }
            return grid;
        }

        private void fill(SectionData d, int kind, int[] grid) {
            if (kind == ESP) {
                for (int i = 0; i < d.espIdx.length; i++) {
                    int raw = d.espState[i];
                    EspCategory category = settings.filter().category(raw);
                    if (category.oreLayer() ? settings.ore() : settings.block()) {
                        grid[d.espIdx[i]] = settings.filter().group(raw) + 2; // +2: never 0, and unknown (-1) still counts
                    }
                }
            } else if (kind == FLUID) {
                for (int i = 0; i < 4096; i++) {
                    grid[i] = SectionData.bit(d.lava, i) ? 1 : SectionData.bit(d.water, i) ? 2 : 0;
                }
            } else {
                for (int i = 0; i < 4096; i++) {
                    grid[i] = SectionData.bit(d.cave, i) ? 1 : 0;
                }
            }
        }

        /** True if the block at local (x, y, z), which may be outside this section, is of kind {@code id}. */
        boolean same(int x, int y, int z, int id, int kind) {
            int ox = x < 0 ? -1 : x > 15 ? 1 : 0;
            int oy = y < 0 ? -1 : y > 15 ? 1 : 0;
            int oz = z < 0 ? -1 : z > 15 ? 1 : 0;
            int[] grid = grid(ox, oy, oz, kind);
            return grid[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)] == id;
        }

        void block(int x, int y, int z, int id, int kind, int rgb, XrayConfig.EspStyle style, int lineAlpha, int fillAlpha, Ints lines, Ints fill) {
            int lineColor = lineAlpha << 24 | (rgb & 0xFFFFFF);
            int fillColor = fillAlpha << 24 | (rgb & 0xFFFFFF);
            boolean outline = style != XrayConfig.EspStyle.FILLED;
            boolean filled = fill != null && fillAlpha > 0 && (style == XrayConfig.EspStyle.FILLED || style == XrayConfig.EspStyle.OUTLINE_FILL);
            boolean corners = style == XrayConfig.EspStyle.CORNERS;
            for (int n = 0; n < 6; n++) {
                if (same(x + DX[n], y + DY[n], z + DZ[n], id, kind)) {
                    continue; // face hidden inside the vein
                }
                if (filled) {
                    face(fill, x, y, z, n, fillColor);
                }
                if (!outline) {
                    continue;
                }
                for (int t = 0; t < 6; t++) {
                    if (t == n || t == (n ^ 1)) {
                        continue; // only the four directions along the face
                    }
                    boolean bt = same(x + DX[t], y + DY[t], z + DZ[t], id, kind);
                    boolean btn = bt && same(x + DX[t] + DX[n], y + DY[t] + DY[n], z + DZ[t] + DZ[n], id, kind);
                    int partner;
                    if (bt && !btn) {
                        continue; // flat surface goes on: no edge here
                    } else if (bt) {
                        partner = t ^ 1; // inside corner, shared with the face of the block around it
                    } else {
                        partner = t; // outside edge, shared with this block's own face towards t
                    }
                    if (n < partner) { // each edge belongs to two faces: draw it once
                        edge(lines, x, y, z, n, t, lineColor, corners);
                    }
                }
            }
        }

        private static int axis(int dir) {
            return dir < 2 ? 1 : dir < 4 ? 2 : 0;
        }

        private static boolean positive(int dir) {
            return (dir & 1) == 1;
        }

        private static void edge(Ints out, int x, int y, int z, int n, int t, int color, boolean corners) {
            float[] a = {x, y, z};
            int an = axis(n), at = axis(t), aw = 3 - an - at;
            a[an] += positive(n) ? 1 : 0;
            a[at] += positive(t) ? 1 : 0;
            float[] b = a.clone();
            b[aw] += 1;
            if (!corners) {
                line(out, a, b, color);
                return;
            }
            float[] a2 = a.clone();
            a2[aw] += 0.25f;
            float[] b2 = b.clone();
            b2[aw] -= 0.25f;
            line(out, a, a2, color);
            line(out, b2, b, color);
        }

        private static void face(Ints out, int x, int y, int z, int n, int color) {
            int an = axis(n);
            int au = (an + 1) % 3, av = (an + 2) % 3;
            float[] p = {x, y, z};
            p[an] += positive(n) ? 1 : 0;
            for (int corner = 0; corner < 4; corner++) {
                float[] q = p.clone();
                q[au] += corner == 1 || corner == 2 ? 1 : 0;
                q[av] += corner >= 2 ? 1 : 0;
                out.vertex(q[0], q[1], q[2], color);
            }
        }

        private static void line(Ints out, float[] a, float[] b, int color) {
            out.vertex(a[0], a[1], a[2], color);
            out.vertex(b[0], b[1], b[2], color);
        }

        static void dot(Ints out, float x, float y, float z, int color) {
            float r = 0.15f;
            out.vertex(x - r, y, z, color);
            out.vertex(x + r, y, z, color);
            out.vertex(x, y - r, z, color);
            out.vertex(x, y + r, z, color);
            out.vertex(x, y, z - r, color);
            out.vertex(x, y, z + r, color);
        }
    }

    /** A growable int array: 4 ints per vertex. */
    static final class Ints {
        private int[] data = MeshData.NONE;
        private int size;

        void vertex(float x, float y, float z, int argb) {
            if (size + 4 > data.length) {
                data = java.util.Arrays.copyOf(data, Math.max(64, data.length * 2));
            }
            data[size++] = Float.floatToRawIntBits(x);
            data[size++] = Float.floatToRawIntBits(y);
            data[size++] = Float.floatToRawIntBits(z);
            data[size++] = argb;
        }

        int[] toArray() {
            return size == 0 ? MeshData.NONE : java.util.Arrays.copyOf(data, size);
        }
    }
}
