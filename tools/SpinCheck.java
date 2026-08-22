/**
 * Validates the angular-velocity recovery in ShipState.updateSpin and the
 * point-velocity query in ShipCtx.velocityAt.
 *
 * The quaternion algebra is reproduced here (JOML's mul/conjugate are plain
 * Hamilton products) so the logic can be exercised without a Minecraft runtime.
 */
public class SpinCheck {

    record Q(double x, double y, double z, double w) {
        Q mul(Q o) { // Hamilton product, matching JOML's Quaterniond.mul
            return new Q(
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w,
                w * o.w - x * o.x - y * o.y - z * o.z);
        }
        Q conj() { return new Q(-x, -y, -z, w); }
        static Q axisAngle(double ax, double ay, double az, double angle) {
            double n = Math.sqrt(ax * ax + ay * ay + az * az);
            ax /= n; ay /= n; az /= n;
            double s = Math.sin(angle / 2);
            return new Q(ax * s, ay * s, az * s, Math.cos(angle / 2));
        }
        Q negate() { return new Q(-x, -y, -z, -w); }
    }

    record V(double x, double y, double z) {
        V cross(V o) {
            return new V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
        }
        V add(V o) { return new V(x + o.x, y + o.y, z + o.z); }
        double len() { return Math.sqrt(x * x + y * y + z * z); }
    }

    /** Verbatim from ShipState.updateSpin. */
    static V omega(Q prev, Q current) {
        Q spin = current.mul(prev.conj());
        double w = spin.w, x = spin.x, y = spin.y, z = spin.z;
        if (w < 0.0) { w = -w; x = -x; y = -y; z = -z; }
        double sin = Math.sqrt(x * x + y * y + z * z);
        double angle = 2.0 * Math.atan2(sin, w);
        if (sin > 1.0e-9 && angle < 1.0) {
            double scale = angle / sin;
            return new V(x * scale, y * scale, z * scale);
        }
        return new V(0, 0, 0);
    }

    /** Verbatim from ShipCtx.velocityAt. */
    static V velocityAt(V velocity, V angular, V center, V point) {
        if (angular.x * angular.x + angular.y * angular.y + angular.z * angular.z < 1e-12) {
            return velocity;
        }
        V r = new V(point.x - center.x, point.y - center.y, point.z - center.z);
        return velocity.add(angular.cross(r));
    }

    static int failures = 0;

    static void check(String label, boolean ok, String detail) {
        System.out.printf("  %-48s %s%s%n", label, ok ? "ok" : "FAIL", ok ? "" : "  <- " + detail);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        System.out.println("Angular velocity recovery");

        // 5 degrees per tick about +Y.
        double perTick = Math.toRadians(5);
        Q a = Q.axisAngle(0, 1, 0, 1.234);                 // arbitrary start
        Q b = Q.axisAngle(0, 1, 0, 1.234 + perTick);
        V w = omega(a, b);
        check("magnitude matches 5 deg/tick", Math.abs(w.len() - perTick) < 1e-9,
                "got " + Math.toDegrees(w.len()));
        check("axis is +Y", Math.abs(w.y - perTick) < 1e-9 && Math.abs(w.x) < 1e-9,
                "got (" + w.x + "," + w.y + "," + w.z + ")");

        // Reverse rotation must reverse the axis.
        V wRev = omega(b, a);
        check("reversed rotation flips the axis", Math.abs(wRev.y + perTick) < 1e-9,
                "got " + wRev.y);

        // The double cover: q and -q are the same rotation, so the shortest arc
        // must be taken rather than the 2*pi complement.
        V wNeg = omega(a, b.negate());
        check("shortest arc used when q is negated", Math.abs(wNeg.y - perTick) < 1e-9,
                "got " + wNeg.y);

        // Tilted axis.
        Q c = Q.axisAngle(1, 1, 0, 0.0);
        Q d = Q.axisAngle(1, 1, 0, perTick);
        V wt = omega(c, d);
        double inv = 1.0 / Math.sqrt(2);
        check("tilted axis recovered", Math.abs(wt.x - perTick * inv) < 1e-9
                        && Math.abs(wt.y - perTick * inv) < 1e-9,
                "got (" + wt.x + "," + wt.y + "," + wt.z + ")");

        // A re-assembly or teleport must not register as spin.
        Q far = Q.axisAngle(0, 1, 0, 2.0);
        check("1.5 rad jump rejected as a teleport",
                omega(Q.axisAngle(0, 1, 0, 0.5), far).len() == 0.0, "not rejected");

        // No rotation at all.
        check("identical orientation gives zero spin", omega(a, a).len() < 1e-12, "nonzero");

        System.out.println("Point velocity on a rotating hull");

        // A 20-block half-span wing rolling at 5 deg/tick about the forward (X) axis.
        V center = new V(0, 100, 0);
        V hull = new V(0.5, 0, 0);              // 10 m/s forward, blocks/tick
        V roll = new V(perTick, 0, 0);
        V tipUp = new V(0, 100, 20);            // starboard tip, 20 blocks out
        V tipDown = new V(0, 100, -20);

        V vUp = velocityAt(hull, roll, center, tipUp);
        V vDown = velocityAt(hull, roll, center, tipDown);
        double expected = perTick * 20;         // |omega| * r, blocks/tick

        check("rising tip gains omega*r", Math.abs(Math.abs(vUp.y) - expected) < 1e-9,
                "got " + vUp.y);
        check("tips move opposite each other", vUp.y * vDown.y < 0,
                "same sign: " + vUp.y + " / " + vDown.y);
        check("hull translation preserved at the tip", Math.abs(vUp.x - 0.5) < 1e-9,
                "got " + vUp.x);

        double hullSpeed = hull.len() * 20;
        double tipSpeed = vUp.len() * 20;
        System.out.printf("  hull %.1f m/s -> rolling wingtip %.1f m/s (%.0f%% faster)%n",
                hullSpeed, tipSpeed, (tipSpeed / hullSpeed - 1) * 100);
        check("rolling tip outruns the hull", tipSpeed > hullSpeed * 1.2,
                "tip=" + tipSpeed);

        // Centre of mass must be unaffected by spin.
        V vCenter = velocityAt(hull, roll, center, center);
        check("centre of mass unaffected by spin", Math.abs(vCenter.y) < 1e-12, "got " + vCenter.y);

        System.out.println();
        System.out.println(failures == 0 ? "all checks passed" : failures + " CHECK(S) FAILED");
        if (failures > 0) System.exit(1);
    }
}
