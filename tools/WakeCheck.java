/**
 * Validates the wake turbulence field: the Rankine vortex profile, the
 * counter-rotating pair signature, lifetime decay, and - most importantly -
 * that the spatial hash finds exactly the same cores a brute-force scan would.
 *
 * A hash that silently misses cores would degrade the effect in a way that is
 * almost impossible to spot by eye, so it is checked against ground truth over
 * randomised layouts rather than assumed correct.
 *
 * The physics and hashing below are reproduced from WakeField.
 */
import java.util.Random;

public class WakeCheck {

    static final double CELL = 16.0;
    static final double MAX_REACH = 12.0;
    static final int TABLE = 2048;
    static final int MAX_CORES = 1024;

    static double[] px = new double[MAX_CORES], py = new double[MAX_CORES], pz = new double[MAX_CORES];
    static double[] ax = new double[MAX_CORES], ay = new double[MAX_CORES], az = new double[MAX_CORES];
    static double[] strength = new double[MAX_CORES], coreRadius = new double[MAX_CORES];
    static double[] reach = new double[MAX_CORES], halfLength = new double[MAX_CORES];
    static int[] age = new int[MAX_CORES], life = new int[MAX_CORES], owner = new int[MAX_CORES];
    static int[] head = new int[TABLE], next = new int[MAX_CORES];
    static int liveCount;

    static double outX, outY, outZ;

    static int floorDiv(double v) { return (int) Math.floor(v / CELL); }

    static int hashCell(int cx, int cy, int cz) {
        int h = (cx * 73856093) ^ (cy * 19349663) ^ (cz * 83492791);
        return h & (TABLE - 1);
    }

    static void rebuild() {
        java.util.Arrays.fill(head, -1);
        liveCount = 0;
        for (int i = 0; i < MAX_CORES; i++) {
            if (life[i] <= 0) continue;
            liveCount++;
            int cell = hashCell(floorDiv(px[i]), floorDiv(py[i]), floorDiv(pz[i]));
            next[i] = head[cell];
            head[cell] = i;
        }
    }

    /** Verbatim from WakeField.accumulate. */
    static void accumulate(int i, double x, double y, double z, int ignoreOwner) {
        if (ignoreOwner != 0 && owner[i] == ignoreOwner && age[i] < 40) return;
        double dx = x - px[i], dy = y - py[i], dz = z - pz[i];
        double axial = dx * ax[i] + dy * ay[i] + dz * az[i];
        if (axial < -halfLength[i] || axial > halfLength[i]) return;
        double rx = dx - ax[i] * axial, ry = dy - ay[i] * axial, rz = dz - az[i] * axial;
        double rSq = rx * rx + ry * ry + rz * rz;
        double maxR = reach[i];
        if (rSq > maxR * maxR || rSq < 1.0e-8) return;
        double r = Math.sqrt(rSq);
        double rc = coreRadius[i];
        double speed = r < rc ? strength[i] * r / (rc * rc) : strength[i] / r;
        double lifeFrac = 1.0 - (double) age[i] / life[i];
        speed *= lifeFrac * lifeFrac * (1.0 - r / maxR);
        double inv = 1.0 / r;
        double nx = rx * inv, ny = ry * inv, nz = rz * inv;
        outX += (ay[i] * nz - az[i] * ny) * speed;
        outY += (az[i] * nx - ax[i] * nz) * speed;
        outZ += (ax[i] * ny - ay[i] * nx) * speed;
    }

    /** Hashed sample, verbatim from WakeField.sample. */
    static double[] sampleHashed(double x, double y, double z, int ignoreOwner) {
        outX = outY = outZ = 0;
        int bx = floorDiv(x), by = floorDiv(y), bz = floorDiv(z);
        for (int ox = -1; ox <= 1; ox++)
            for (int oy = -1; oy <= 1; oy++)
                for (int oz = -1; oz <= 1; oz++) {
                    int cell = hashCell(bx + ox, by + oy, bz + oz);
                    for (int i = head[cell]; i != -1; i = next[i]) accumulate(i, x, y, z, ignoreOwner);
                }
        return new double[]{ outX, outY, outZ };
    }

    /** Ground truth: every live core, no spatial acceleration. */
    static double[] sampleBrute(double x, double y, double z, int ignoreOwner) {
        outX = outY = outZ = 0;
        for (int i = 0; i < MAX_CORES; i++) {
            if (life[i] <= 0) continue;
            accumulate(i, x, y, z, ignoreOwner);
        }
        return new double[]{ outX, outY, outZ };
    }

    static void clear() {
        java.util.Arrays.fill(life, 0);
        java.util.Arrays.fill(head, -1);
        liveCount = 0;
    }

    static void put(int i, double x, double y, double z, double dirx, double diry, double dirz,
                    double str, double rc, double half, int a, int l, int own) {
        px[i] = x; py[i] = y; pz[i] = z;
        ax[i] = dirx; ay[i] = diry; az[i] = dirz;
        strength[i] = str; coreRadius[i] = rc;
        reach[i] = Math.min(MAX_REACH, rc * 6.0);
        halfLength[i] = half; age[i] = a; life[i] = l; owner[i] = own;
    }

    static int failures = 0;

    static void check(String label, boolean ok, String detail) {
        System.out.printf("  %-50s %s%s%n", label, ok ? "ok" : "FAIL", ok ? "" : "  <- " + detail);
        if (!ok) failures++;
    }

    static double mag(double[] v) { return Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]); }

    public static void main(String[] args) {
        // ---- 1. Rankine profile ----
        System.out.println("Rankine vortex profile");
        clear();
        put(0, 0, 0, 0, 1, 0, 0, 0.4, 2.0, 50, 0, 100, 0);  // tube along +X
        rebuild();

        double peak = 0, peakR = 0;
        double prevInside = -1;
        boolean risesInside = true;
        for (double r = 0.25; r <= 2.0; r += 0.25) {
            double m = mag(sampleHashed(0, 0, r, 0));
            if (m < prevInside) risesInside = false;
            prevInside = m;
        }
        for (double r = 0.25; r <= MAX_REACH; r += 0.05) {
            double m = mag(sampleHashed(0, 0, r, 0));
            if (m > peak) { peak = m; peakR = r; }
        }
        check("velocity rises linearly inside the core", risesInside, "not monotonic");
        check("peak sits at the core radius (2.0)", Math.abs(peakR - 2.0) < 0.35, "peak at r=" + peakR);

        double at4 = mag(sampleHashed(0, 0, 4, 0));
        double at8 = mag(sampleHashed(0, 0, 8, 0));
        check("velocity falls off outside the core", at8 < at4, at4 + " -> " + at8);
        check("no influence beyond reach", mag(sampleHashed(0, 0, MAX_REACH + 1, 0)) == 0.0, "leaked");
        check("no influence past the tube ends",
                mag(sampleHashed(100, 0, 2, 0)) == 0.0, "leaked axially");

        // Swirl must be tangential: perpendicular to both the axis and the radius.
        double[] v = sampleHashed(0, 0, 2, 0);
        check("flow is tangential (perpendicular to axis)", Math.abs(v[0]) < 1e-9, "axial component " + v[0]);
        check("flow is tangential (perpendicular to radius)", Math.abs(v[2]) < 1e-9, "radial component " + v[2]);

        // ---- 2. counter-rotating pair ----
        System.out.println("Counter-rotating wingtip pair");
        clear();
        // Tubes along +X at z = -10 and z = +10, opposite circulation.
        put(0, 0, 0, -10, 1, 0, 0,  0.4, 2.0, 50, 0, 100, 0);
        put(1, 0, 0,  10, 1, 0, 0, -0.4, 2.0, 50, 0, 100, 0);
        rebuild();
        double[] between = sampleHashed(0, 0, 0, 0);
        check("pair induces vertical flow between the tips", Math.abs(between[1]) > 1e-6,
                "got " + between[1]);
        double[] outboard = sampleHashed(0, 0, 18, 0);
        check("flow reverses outboard of a tip", between[1] * outboard[1] < 0,
                "between=" + between[1] + " outboard=" + outboard[1]);

        // ---- 3. lifetime decay ----
        System.out.println("Lifetime decay");
        clear();
        put(0, 0, 0, 0, 1, 0, 0, 0.4, 2.0, 50, 0, 100, 0);
        rebuild();
        double fresh = mag(sampleHashed(0, 0, 2, 0));
        put(0, 0, 0, 0, 1, 0, 0, 0.4, 2.0, 50, 50, 100, 0);
        rebuild();
        double half = mag(sampleHashed(0, 0, 2, 0));
        put(0, 0, 0, 0, 1, 0, 0, 0.4, 2.0, 50, 99, 100, 0);
        rebuild();
        double old = mag(sampleHashed(0, 0, 2, 0));
        check("strength decays with age", fresh > half && half > old, fresh + " " + half + " " + old);
        check("nearly expired core is almost gone", old < fresh * 0.05, "old=" + old);

        // ---- 4. own-wake suppression ----
        System.out.println("Own-wake suppression");
        clear();
        put(0, 0, 0, 0, 1, 0, 0, 0.4, 2.0, 50, 10, 100, 777);
        rebuild();
        check("fresh own wake ignored by its shedder",
                mag(sampleHashed(0, 0, 2, 777)) == 0.0, "not ignored");
        check("fresh own wake still affects others",
                mag(sampleHashed(0, 0, 2, 555)) > 0.0, "wrongly ignored");
        put(0, 0, 0, 0, 1, 0, 0, 0.4, 2.0, 50, 60, 100, 777);
        rebuild();
        check("aged own wake affects its shedder again",
                mag(sampleHashed(0, 0, 2, 777)) > 0.0, "still ignored");

        // ---- 5. spatial hash equivalence ----
        System.out.println("Spatial hash vs brute force (randomised)");
        Random rng = new Random(20260822L);
        int worst = 0;
        double worstErr = 0;
        for (int trial = 0; trial < 200; trial++) {
            clear();
            int n = 1 + rng.nextInt(120);
            for (int i = 0; i < n; i++) {
                double dx = rng.nextGaussian(), dy = rng.nextGaussian(), dz = rng.nextGaussian();
                double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (len < 1e-6) { dx = 1; dy = 0; dz = 0; len = 1; }
                put(i,
                    (rng.nextDouble() - 0.5) * 400,
                    (rng.nextDouble() - 0.5) * 400,
                    (rng.nextDouble() - 0.5) * 400,
                    dx/len, dy/len, dz/len,
                    0.1 + rng.nextDouble() * 0.6,
                    0.8 + rng.nextDouble() * 3.2,
                    1.5 + rng.nextDouble() * 6,
                    rng.nextInt(90), 100, 0);
            }
            rebuild();
            for (int q = 0; q < 25; q++) {
                double x = (rng.nextDouble() - 0.5) * 400;
                double y = (rng.nextDouble() - 0.5) * 400;
                double z = (rng.nextDouble() - 0.5) * 400;
                double[] h = sampleHashed(x, y, z, 0).clone();
                double[] b = sampleBrute(x, y, z, 0).clone();
                double err = Math.abs(h[0]-b[0]) + Math.abs(h[1]-b[1]) + Math.abs(h[2]-b[2]);
                if (err > worstErr) { worstErr = err; }
                if (err > 1e-12) worst++;
            }
        }
        System.out.printf("  5000 queries over 200 layouts, worst error %.3e%n", worstErr);
        check("hashed sampling matches brute force exactly", worst == 0,
                worst + " mismatches");

        // Boundary stress: cores placed exactly on cell edges.
        clear();
        for (int i = 0; i < 40; i++) {
            put(i, i * CELL, 0, 0, 0, 0, 1, 0.4, 2.0, 6, 0, 100, 0);
        }
        rebuild();
        int mismatches = 0;
        for (double x = -5; x < 40 * CELL + 5; x += 0.37) {
            double[] h = sampleHashed(x, 0.0, 3.0, 0).clone();
            double[] b = sampleBrute(x, 0.0, 3.0, 0).clone();
            if (Math.abs(h[0]-b[0]) + Math.abs(h[1]-b[1]) + Math.abs(h[2]-b[2]) > 1e-12) mismatches++;
        }
        check("cores on cell boundaries are still found", mismatches == 0,
                mismatches + " mismatches");
        check("reach stays below the cell size", MAX_REACH < CELL,
                "reach " + MAX_REACH + " >= cell " + CELL);

        System.out.println();
        System.out.println(failures == 0 ? "all checks passed" : failures + " CHECK(S) FAILED");
        if (failures > 0) System.exit(1);
    }
}
