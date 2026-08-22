package dev.levi.vaportrails.sable;

import dev.levi.vaportrails.fx.HullShape;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link HullShape} for one ship by walking the blocks in its plot.
 *
 * The scan is incremental: it processes a small budget of chunk sections per
 * client tick and only publishes a finished shape once the whole ship has been
 * covered. Doing it in a single pass would mean a multi-millisecond hitch every
 * time a large ship refreshed, which is exactly the sort of stutter a purely
 * cosmetic mod has no business causing.
 */
final class HullScan {

    /** Ships wider than this in local blocks fall back to the bounding box. */
    private static final int MAX_SPAN = 192;
    /** Non-empty sections processed per tick, per ship. */
    private static final int SECTION_BUDGET = 6;

    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    private final boolean[] filled;
    private final short[] floorY;
    private final short[] ceilY;

    private final List<LevelChunk> pending = new ArrayList<>();
    private int chunkCursor;
    private int sectionCursor;
    private int found;

    private HullScan(int minX, int minZ, int sizeX, int sizeZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        int cells = sizeX * sizeZ;
        this.filled = new boolean[cells];
        this.floorY = new short[cells];
        this.ceilY = new short[cells];
    }

    /** Begin a scan, or null when the ship has no usable plot bounds. */
    static HullScan start(ClientSubLevel sub) {
        LevelPlot plot = sub.getPlot();
        if (plot == null) {
            return null;
        }
        BoundingBox3ic bounds = plot.getBoundingBox();
        if (bounds == null) {
            return null;
        }
        int sizeX = bounds.maxX() - bounds.minX() + 1;
        int sizeZ = bounds.maxZ() - bounds.minZ() + 1;
        if (sizeX <= 0 || sizeZ <= 0 || sizeX > MAX_SPAN || sizeZ > MAX_SPAN) {
            return null;
        }
        HullScan scan = new HullScan(bounds.minX(), bounds.minZ(), sizeX, sizeZ);
        for (PlotChunkHolder holder : plot.getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk != null) {
                scan.pending.add(chunk);
            }
        }
        return scan.pending.isEmpty() ? null : scan;
    }

    /** Advance the scan; true once every chunk has been covered. */
    boolean step() {
        int budget = SECTION_BUDGET;
        while (chunkCursor < pending.size()) {
            LevelChunk chunk = pending.get(chunkCursor);
            LevelChunkSection[] sections = chunk.getSections();
            ChunkPos pos = chunk.getPos();
            int baseX = pos.getMinBlockX();
            int baseZ = pos.getMinBlockZ();

            while (sectionCursor < sections.length) {
                LevelChunkSection section = sections[sectionCursor];
                int baseY = chunk.getMinBuildHeight() + sectionCursor * 16;
                sectionCursor++;
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                scanSection(section, baseX, baseY, baseZ);
                if (--budget <= 0) {
                    return false;
                }
            }
            sectionCursor = 0;
            chunkCursor++;
        }
        return true;
    }

    private void scanSection(LevelChunkSection section, int baseX, int baseY, int baseZ) {
        for (int y = 0; y < 16; y++) {
            int localY = baseY + y;
            for (int z = 0; z < 16; z++) {
                int dz = baseZ + z - minZ;
                if (dz < 0 || dz >= sizeZ) {
                    continue;
                }
                for (int x = 0; x < 16; x++) {
                    if (section.getBlockState(x, y, z).isAir()) {
                        continue;
                    }
                    int dx = baseX + x - minX;
                    if (dx < 0 || dx >= sizeX) {
                        continue;
                    }
                    int idx = dx * sizeZ + dz;
                    if (!filled[idx]) {
                        filled[idx] = true;
                        floorY[idx] = (short) localY;
                        ceilY[idx] = (short) localY;
                        found++;
                    } else if (localY < floorY[idx]) {
                        floorY[idx] = (short) localY;
                    } else if (localY > ceilY[idx]) {
                        ceilY[idx] = (short) localY;
                    }
                }
            }
        }
    }

    /** Turn the completed occupancy grid into an immutable shape. */
    HullShape finish() {
        if (found == 0) {
            return null;
        }
        int[] columns = new int[found];
        // Walking x-major yields points already ordered by x then z, which is
        // exactly the ordering the monotone chain below expects.
        int n = 0;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                if (filled[dx * sizeZ + dz]) {
                    columns[n++] = (dx << 16) | dz;
                }
            }
        }

        int[] hull = convexHull(columns);
        float[] hx = new float[hull.length];
        float[] hy = new float[hull.length];
        float[] hz = new float[hull.length];
        for (int i = 0; i < hull.length; i++) {
            int packed = columns[hull[i]];
            int dx = packed >>> 16;
            int dz = packed & 0xFFFF;
            hx[i] = minX + dx + 0.5f;
            hz[i] = minZ + dz + 0.5f;
            hy[i] = (floorY[dx * sizeZ + dz] + ceilY[dx * sizeZ + dz]) * 0.5f;
        }
        return new HullShape(minX, minZ, sizeZ, columns, floorY, ceilY, hx, hy, hz);
    }

    /** Monotone chain hull over the occupied columns; returns indices into {@code columns}. */
    private static int[] convexHull(int[] columns) {
        int n = columns.length;
        if (n < 3) {
            int[] all = new int[n];
            for (int i = 0; i < n; i++) {
                all[i] = i;
            }
            return all;
        }
        int[] stack = new int[n * 2 + 1];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(columns[stack[k - 2]], columns[stack[k - 1]], columns[i]) <= 0) {
                k--;
            }
            stack[k++] = i;
        }
        int lower = k + 1;
        for (int i = n - 2; i >= 0; i--) {
            while (k >= lower && cross(columns[stack[k - 2]], columns[stack[k - 1]], columns[i]) <= 0) {
                k--;
            }
            stack[k++] = i;
        }
        // The chain closes on its starting point; drop the duplicate.
        int count = Math.max(1, k - 1);
        int[] out = new int[count];
        System.arraycopy(stack, 0, out, 0, count);
        return out;
    }

    private static long cross(int a, int b, int c) {
        long ax = a >>> 16;
        long az = a & 0xFFFF;
        long bx = b >>> 16;
        long bz = b & 0xFFFF;
        long cx = c >>> 16;
        long cz = c & 0xFFFF;
        return (bx - ax) * (cz - az) - (bz - az) * (cx - ax);
    }
}
