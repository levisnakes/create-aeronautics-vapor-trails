package dev.levi.vaportrails.fx;

import net.minecraft.util.RandomSource;

/**
 * The actual silhouette of a ship's blocks, in ship-local block space.
 *
 * Effects used to work off the world-axis-aligned bounding box, which is a poor
 * stand-in for a real airframe: the corner of the box on a cross-shaped plane
 * is empty air, and the box inflates as soon as the ship rotates. This stores
 * which local columns actually contain blocks, how low and high each column
 * reaches, and a 2D convex hull of the occupied columns so "what is the
 * outermost point along this direction" is a short loop over a few dozen hull
 * vertices instead of a scan over thousands of columns.
 *
 * Queries take local-horizontal directions. That is deliberate: a wing is a
 * feature of the ship, so a rolled aircraft's tips are still at its local span
 * extremities, and a local-horizontal query axis keeps the 2D hull exact.
 */
public final class HullShape {

    private final int minX;
    private final int minZ;
    private final int sizeZ;

    /** Packed (dx << 16 | dz) grid offsets of every occupied column. */
    private final int[] columns;
    private final short[] floorY;
    private final short[] ceilY;

    /** Convex hull of the occupied columns, in local coordinates. */
    private final float[] hullX;
    private final float[] hullY;
    private final float[] hullZ;

    public HullShape(int minX, int minZ, int sizeZ, int[] columns,
                     short[] floorY, short[] ceilY,
                     float[] hullX, float[] hullY, float[] hullZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.sizeZ = sizeZ;
        this.columns = columns;
        this.floorY = floorY;
        this.ceilY = ceilY;
        this.hullX = hullX;
        this.hullY = hullY;
        this.hullZ = hullZ;
    }

    public boolean valid() {
        return columns.length > 0 && hullX.length > 0;
    }

    public int columnCount() {
        return columns.length;
    }

    /**
     * Outermost point of the hull along a local-horizontal direction, written
     * into {@code out} as local (x, y, z). The y is the mid-height of that
     * column, so a wingtip sits on the wing rather than above or below it.
     */
    public void extreme(double dx, double dz, double[] out) {
        int best = 0;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < hullX.length; i++) {
            double score = hullX[i] * dx + hullZ[i] * dz;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        out[0] = hullX[best];
        out[1] = hullY[best];
        out[2] = hullZ[best];
    }

    /** Half of the hull's width along a local-horizontal direction. */
    public double halfExtent(double dx, double dz) {
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (int i = 0; i < hullX.length; i++) {
            double score = hullX[i] * dx + hullZ[i] * dz;
            if (score < lo) {
                lo = score;
            }
            if (score > hi) {
                hi = score;
            }
        }
        return Math.max(0.5, (hi - lo) * 0.5);
    }

    /**
     * Pick a random occupied column, written into {@code out} as local
     * (x, floorY, z). Sampling real columns means hover dust only ever comes
     * off ground that is genuinely underneath the hull.
     */
    public void pickColumn(RandomSource rng, double[] out) {
        int i = rng.nextInt(columns.length);
        int packed = columns[i];
        int dx = packed >>> 16;
        int dz = packed & 0xFFFF;
        out[0] = minX + dx + 0.5;
        out[1] = floorY[dx * sizeZ + dz];
        out[2] = minZ + dz + 0.5;
    }
}
