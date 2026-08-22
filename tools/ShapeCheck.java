/**
 * Validates the hull silhouette algorithm used by HullScan/HullShape.
 *
 * The monotone-chain hull and the extremity query are reproduced here verbatim
 * from the mod so they can be exercised without a Minecraft runtime. The test
 * shape is a cross-shaped aircraft: a long fuselage with a narrow wing spar,
 * i.e. precisely the case where the bounding box corner sits in empty air.
 */
import java.util.ArrayList;
import java.util.List;

public class ShapeCheck {

    // ---- verbatim from HullScan ----

    private static long cross(int a, int b, int c) {
        long ax = a >>> 16, az = a & 0xFFFF;
        long bx = b >>> 16, bz = b & 0xFFFF;
        long cx = c >>> 16, cz = c & 0xFFFF;
        return (bx - ax) * (cz - az) - (bz - az) * (cx - ax);
    }

    private static int[] convexHull(int[] columns) {
        int n = columns.length;
        if (n < 3) {
            int[] all = new int[n];
            for (int i = 0; i < n; i++) all[i] = i;
            return all;
        }
        int[] stack = new int[n * 2 + 1];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(columns[stack[k - 2]], columns[stack[k - 1]], columns[i]) <= 0) k--;
            stack[k++] = i;
        }
        int lower = k + 1;
        for (int i = n - 2; i >= 0; i--) {
            while (k >= lower && cross(columns[stack[k - 2]], columns[stack[k - 1]], columns[i]) <= 0) k--;
            stack[k++] = i;
        }
        int count = Math.max(1, k - 1);
        int[] out = new int[count];
        System.arraycopy(stack, 0, out, 0, count);
        return out;
    }

    // ---- verbatim from HullShape.extreme / halfExtent ----

    static double[] extreme(float[] hx, float[] hz, double dx, double dz) {
        int best = 0;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < hx.length; i++) {
            double score = hx[i] * dx + hz[i] * dz;
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return new double[]{ hx[best], hz[best] };
    }

    static double halfExtent(float[] hx, float[] hz, double dx, double dz) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = 0; i < hx.length; i++) {
            double s = hx[i] * dx + hz[i] * dz;
            if (s < lo) lo = s;
            if (s > hi) hi = s;
        }
        return Math.max(0.5, (hi - lo) * 0.5);
    }

    // ---- test scaffolding ----

    static int failures = 0;

    static void check(String label, boolean ok, String detail) {
        System.out.printf("  %-44s %s%s%n", label, ok ? "ok" : "FAIL", ok ? "" : "  <- " + detail);
        if (!ok) failures++;
    }

    /** Builds the column list the same way HullScan.finish does: x-major order. */
    static int[] columnsOf(boolean[][] grid) {
        List<Integer> list = new ArrayList<>();
        for (int x = 0; x < grid.length; x++)
            for (int z = 0; z < grid[x].length; z++)
                if (grid[x][z]) list.add((x << 16) | z);
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    static float[][] hullPoints(int[] columns) {
        int[] h = convexHull(columns);
        float[] hx = new float[h.length];
        float[] hz = new float[h.length];
        for (int i = 0; i < h.length; i++) {
            hx[i] = (columns[h[i]] >>> 16) + 0.5f;
            hz[i] = (columns[h[i]] & 0xFFFF) + 0.5f;
        }
        return new float[][]{ hx, hz };
    }

    public static void main(String[] args) {
        // ---- 1. cross-shaped aircraft ----
        // Fuselage: x 0..39, z 9..10.  Wing spar: x 18..21, z 0..19.
        int sx = 40, sz = 20;
        boolean[][] grid = new boolean[sx][sz];
        for (int x = 0; x < sx; x++) { grid[x][9] = true; grid[x][10] = true; }
        for (int x = 18; x <= 21; x++) for (int z = 0; z < sz; z++) grid[x][z] = true;

        int[] cols = columnsOf(grid);
        float[][] hp = hullPoints(cols);
        float[] hx = hp[0], hz = hp[1];

        System.out.println("Cross-shaped airframe (fuselage x0-39 z9-10, wing x18-21 z0-19)");
        System.out.printf("  occupied columns=%d  hull vertices=%d%n", cols.length, hx.length);

        // Every occupied column must lie inside the hull.
        boolean allInside = true;
        for (int c : cols) {
            double px = (c >>> 16) + 0.5, pz = (c & 0xFFFF) + 0.5;
            for (int i = 0; i < hx.length; i++) {
                int j = (i + 1) % hx.length;
                double e = (hx[j] - hx[i]) * (pz - hz[i]) - (hz[j] - hz[i]) * (px - hx[i]);
                if (e < -1e-6) { allInside = false; break; }
            }
            if (!allInside) break;
        }
        check("all occupied columns inside hull", allInside, "hull does not enclose the ship");

        // Wingtip query: straight along +Z should land on the wing spar, not a box corner.
        double[] tip = extreme(hx, hz, 0, 1);
        boolean onWing = tip[1] >= 19.0 && tip[0] >= 18.0 && tip[0] <= 22.0;
        check("+Z extremity lands on the wing spar", onWing,
                "got (" + tip[0] + "," + tip[1] + ")");

        // What the old bounding-box corner would have produced.
        double boxCornerX = sx - 0.0, boxCornerZ = sz - 0.0;
        double err = Math.hypot(boxCornerX - tip[0], boxCornerZ - tip[1]);
        System.out.printf("  box corner would be (%.1f,%.1f) -> %.1f blocks off the real tip%n",
                boxCornerX, boxCornerZ, err);
        check("box corner is measurably wrong (>10 blocks)", err > 10.0, "err=" + err);

        // Nose query along +X should land on the fuselage tip.
        double[] nose = extreme(hx, hz, 1, 0);
        check("+X extremity lands on the fuselage nose",
                nose[0] >= 39.0 && nose[1] >= 9.0 && nose[1] <= 11.0,
                "got (" + nose[0] + "," + nose[1] + ")");

        // Extents.
        check("half extent along X ~= 20", Math.abs(halfExtent(hx, hz, 1, 0) - 20.0) < 0.6,
                "" + halfExtent(hx, hz, 1, 0));
        check("half extent along Z ~= 10", Math.abs(halfExtent(hx, hz, 0, 1) - 10.0) < 0.6,
                "" + halfExtent(hx, hz, 0, 1));

        // ---- 2. degenerate shapes ----
        System.out.println("Degenerate shapes");
        boolean[][] single = new boolean[1][1];
        single[0][0] = true;
        float[][] sp = hullPoints(columnsOf(single));
        check("single column survives", sp[0].length >= 1, "empty hull");

        boolean[][] line = new boolean[10][1];
        for (int x = 0; x < 10; x++) line[x][0] = true;
        float[][] lp = hullPoints(columnsOf(line));
        double[] lineTip = extreme(lp[0], lp[1], 1, 0);
        check("collinear row keeps its endpoint", lineTip[0] >= 9.0, "got " + lineTip[0]);

        // ---- 3. a solid rectangle should match the box exactly ----
        System.out.println("Solid rectangle (should agree with the bounding box)");
        boolean[][] rect = new boolean[12][8];
        for (int x = 0; x < 12; x++) for (int z = 0; z < 8; z++) rect[x][z] = true;
        float[][] rp = hullPoints(columnsOf(rect));
        check("rect half extent X ~= 6", Math.abs(halfExtent(rp[0], rp[1], 1, 0) - 6.0) < 0.6,
                "" + halfExtent(rp[0], rp[1], 1, 0));
        check("rect hull is 4 corners", rp[0].length == 4, "vertices=" + rp[0].length);

        System.out.println();
        System.out.println(failures == 0 ? "all checks passed" : failures + " CHECK(S) FAILED");
        if (failures > 0) System.exit(1);
    }
}
