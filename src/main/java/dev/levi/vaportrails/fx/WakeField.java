package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.VTConfig;
import net.minecraft.world.phys.Vec3;

/**
 * Persistent wake turbulence: the sky remembers where ships have flown.
 *
 * A fast wing sheds a pair of counter-rotating vortex tubes from its tips.
 * They outlive the visible condensation by a long way, sink slowly, and stay
 * strong enough to throw anything that flies through them around. This stores
 * those tubes as a trail of vortex cores and feeds their induced flow back
 * into {@link AirField}, so a ship crossing another's wake - or its own, on a
 * tight turn - visibly tears its trails apart.
 *
 * Cores use a Rankine profile: solid-body rotation inside the core radius and
 * a 1/r falloff outside, which avoids the singularity a raw line vortex has at
 * its centre. A uniform spatial hash keeps sampling cheap; without it every
 * particle would test every core every tick.
 */
public final class WakeField {

    private static final int MAX_CORES = 1024;
    /** Hash table size; must be a power of two. */
    private static final int TABLE = 2048;
    /** Spatial hash cell size in blocks. Must exceed the largest core reach. */
    static final double CELL = 16.0;
    /** Hard ceiling on a core's reach, so one cell neighbourhood always suffices. */
    static final double MAX_REACH = 12.0;

    private static final double[] px = new double[MAX_CORES];
    private static final double[] py = new double[MAX_CORES];
    private static final double[] pz = new double[MAX_CORES];
    private static final double[] ax = new double[MAX_CORES];
    private static final double[] ay = new double[MAX_CORES];
    private static final double[] az = new double[MAX_CORES];
    private static final double[] strength = new double[MAX_CORES];
    private static final double[] coreRadius = new double[MAX_CORES];
    private static final double[] reach = new double[MAX_CORES];
    private static final double[] halfLength = new double[MAX_CORES];
    private static final int[] age = new int[MAX_CORES];
    private static final int[] life = new int[MAX_CORES];
    private static final int[] owner = new int[MAX_CORES];

    private static final int[] head = new int[TABLE];
    private static final int[] next = new int[MAX_CORES];

    /** Ring-buffer write cursor; the slot it points at is always the oldest. */
    private static int cursor;
    private static int liveCount;

    private static double outX;
    private static double outY;
    private static double outZ;

    static {
        java.util.Arrays.fill(head, -1);
    }

    /**
     * Shed one vortex core.
     *
     * @param pos       where the core sits
     * @param axis      unit vector along the tube (the shedding ship's heading)
     * @param circulation signed swirl strength; the two wingtips of one ship
     *                    must pass opposite signs so the pair counter-rotates
     * @param span      the shedding ship's width, which sets the core size
     * @param spacing   distance to the previous core, so tubes join up
     * @param ownerId   identity of the shedding ship, so it can ignore the
     *                  wake it is still inside of
     */
    public static void deposit(Vec3 pos, Vec3 axis, double circulation, double span,
                               double spacing, int ownerId, int lifeTicks) {
        int i = cursor;
        cursor = (cursor + 1) % MAX_CORES;
        if (life[i] <= 0) {
            liveCount++;
        }
        px[i] = pos.x;
        py[i] = pos.y;
        pz[i] = pos.z;
        ax[i] = axis.x;
        ay[i] = axis.y;
        az[i] = axis.z;
        double rc = Mth_clamp(span * 0.08, 0.8, 4.0);
        coreRadius[i] = rc;
        reach[i] = Math.min(MAX_REACH, rc * 6.0);
        halfLength[i] = Math.max(1.5, spacing * 0.6);
        strength[i] = circulation;
        age[i] = 0;
        life[i] = lifeTicks;
        owner[i] = ownerId;
    }

    /** Age, sink and re-index every core. Call once per client tick. */
    public static void tick() {
        java.util.Arrays.fill(head, -1);
        if (liveCount == 0) {
            return;
        }
        liveCount = 0;
        for (int i = 0; i < MAX_CORES; i++) {
            if (life[i] <= 0) {
                continue;
            }
            if (++age[i] >= life[i]) {
                life[i] = 0;
                continue;
            }
            // Real vortex pairs descend under their own induced flow.
            py[i] -= 0.015;
            liveCount++;

            int cell = hash(px[i], py[i], pz[i]);
            next[i] = head[cell];
            head[cell] = i;
        }
    }

    /**
     * Accumulate the induced flow at a point. Results are read back through
     * {@link #outX()}, {@link #outY()} and {@link #outZ()}.
     *
     * @param ignoreOwner ship id whose own fresh wake should be skipped, or 0
     */
    public static void sample(double x, double y, double z, int ignoreOwner) {
        outX = 0.0;
        outY = 0.0;
        outZ = 0.0;
        if (liveCount == 0) {
            return;
        }
        int baseX = floorDiv(x);
        int baseY = floorDiv(y);
        int baseZ = floorDiv(z);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                for (int oz = -1; oz <= 1; oz++) {
                    int cell = hashCell(baseX + ox, baseY + oy, baseZ + oz);
                    for (int i = head[cell]; i != -1; i = next[i]) {
                        accumulate(i, x, y, z, ignoreOwner);
                    }
                }
            }
        }
    }

    private static void accumulate(int i, double x, double y, double z, int ignoreOwner) {
        // A ship is always sitting in the wake it just shed; ignore it briefly
        // so it does not shake itself apart the moment it starts moving.
        if (ignoreOwner != 0 && owner[i] == ignoreOwner && age[i] < 40) {
            return;
        }
        double dx = x - px[i];
        double dy = y - py[i];
        double dz = z - pz[i];

        double axial = dx * ax[i] + dy * ay[i] + dz * az[i];
        if (axial < -halfLength[i] || axial > halfLength[i]) {
            return;
        }
        double rx = dx - ax[i] * axial;
        double ry = dy - ay[i] * axial;
        double rz = dz - az[i] * axial;
        double rSq = rx * rx + ry * ry + rz * rz;
        double maxR = reach[i];
        if (rSq > maxR * maxR || rSq < 1.0e-8) {
            return;
        }
        double r = Math.sqrt(rSq);

        // Rankine profile: solid body inside the core, 1/r outside.
        double rc = coreRadius[i];
        double speed = r < rc ? strength[i] * r / (rc * rc) : strength[i] / r;
        // Fade out over the core's life, and taper at the edge of its reach so
        // flying past does not switch the flow on and off abruptly.
        double lifeFrac = 1.0 - (double) age[i] / life[i];
        speed *= lifeFrac * lifeFrac * (1.0 - r / maxR);

        double inv = 1.0 / r;
        double nx = rx * inv;
        double ny = ry * inv;
        double nz = rz * inv;
        // tangent = axis x radialUnit
        outX += (ay[i] * nz - az[i] * ny) * speed;
        outY += (az[i] * nx - ax[i] * nz) * speed;
        outZ += (ax[i] * ny - ay[i] * nx) * speed;
    }

    /** Strength of the disturbance at a point, in blocks per tick. */
    public static double disturbance(double x, double y, double z, int ignoreOwner) {
        sample(x, y, z, ignoreOwner);
        return Math.sqrt(outX * outX + outY * outY + outZ * outZ);
    }

    public static double outX() {
        return outX;
    }

    public static double outY() {
        return outY;
    }

    public static double outZ() {
        return outZ;
    }

    public static boolean idle() {
        return liveCount == 0;
    }

    public static int liveCount() {
        return liveCount;
    }

    public static void reset() {
        java.util.Arrays.fill(life, 0);
        java.util.Arrays.fill(head, -1);
        cursor = 0;
        liveCount = 0;
    }

    /** Ticks a shed core survives, from config. */
    public static int lifeTicks() {
        return (int) (VTConfig.WAKE_TURB_LIFETIME.get() * 20);
    }

    // ---- spatial hash ----

    static int floorDiv(double v) {
        return (int) Math.floor(v / CELL);
    }

    static int hash(double x, double y, double z) {
        return hashCell(floorDiv(x), floorDiv(y), floorDiv(z));
    }

    static int hashCell(int cx, int cy, int cz) {
        int h = (cx * 73856093) ^ (cy * 19349663) ^ (cz * 83492791);
        return h & (TABLE - 1);
    }

    private static double Mth_clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private WakeField() {}
}
