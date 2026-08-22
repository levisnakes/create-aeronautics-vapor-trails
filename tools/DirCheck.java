/**
 * Cross-checks Vapor Trails' propeller wash direction against the formulas
 * decompiled from Create Aeronautics + Sable.
 *
 * Ground truth chain:
 *   getDirectionIndependentSpeed() = step * rotationSpeed * 3.3333 * reverseSign
 *   getThrust()                    = configThrust * dirIndepSpeed      (configThrust > 0)
 *   getScaledThrust()              = -getThrust() * airflowScaling * airPressure   (both > 0)
 *   applyForces(dir=facing.getNormal(), ...) -> force = facing * scaledThrust
 *
 * Therefore: shipForce direction = facing * -sign(getThrust())
 *            air (wash) direction = -shipForce = facing * +sign(getThrust())
 */
public class DirCheck {

    record Vec(int x, int y, int z) {
        Vec scale(double s) { return new Vec((int) (x * s), (int) (y * s), (int) (z * s)); }
        public String toString() { return "(" + x + "," + y + "," + z + ")"; }
    }

    // name, facing normal, axisDirection step
    record Facing(String name, Vec normal, int step) {}

    static final Facing[] FACINGS = {
        new Facing("DOWN",  new Vec(0, -1, 0), -1),
        new Facing("UP",    new Vec(0,  1, 0),  1),
        new Facing("NORTH", new Vec(0, 0, -1), -1),
        new Facing("SOUTH", new Vec(0, 0,  1),  1),
        new Facing("WEST",  new Vec(-1, 0, 0), -1),
        new Facing("EAST",  new Vec(1,  0, 0),  1),
    };

    static final double CONFIG_THRUST = 5.0;   // positive constant
    static final double AIRFLOW_SCALING = 1.0; // positive
    static final double AIR_PRESSURE = 1.0;    // positive

    /** Ground truth: the direction the AIR is pushed. */
    static Vec truthWashDir(Facing f, double rotationSpeed, boolean reversed) {
        double revSign = reversed ? -1 : 1;
        double dirIndep = f.step * rotationSpeed * 3.3333 * revSign;
        double thrust = CONFIG_THRUST * dirIndep;
        double scaled = -thrust * AIRFLOW_SCALING * AIR_PRESSURE;
        // force = facing * scaled  ->  air goes the opposite way
        double airSign = -Math.signum(scaled);
        return f.normal.scale(airSign);
    }

    /** What ShipState.thrustLocal + PropCtx.washDir() produce. */
    static Vec modWashDir(Facing f, double rotationSpeed, boolean reversed) {
        double revSign = reversed ? -1 : 1;
        double thrust = CONFIG_THRUST * (f.step * rotationSpeed * 3.3333 * revSign);

        // --- ShipState.thrustLocal ---
        double sign;
        if (Math.abs(thrust) > 1.0e-6) {
            sign = -Math.signum(thrust);
        } else if (Math.abs(rotationSpeed) > 1.0e-6) {
            sign = -Math.signum(rotationSpeed) * f.step;
        } else {
            sign = 1.0;
        }
        Vec thrustDir = f.normal.scale(sign);

        // --- PropCtx.washDir() = thrustDir.scale(-1) ---
        return thrustDir.scale(-1.0);
    }

    public static void main(String[] args) {
        int pass = 0;
        int fail = 0;
        System.out.printf("%-6s %-8s %-9s | %-12s %-12s %s%n",
                "facing", "rotSpeed", "reversed", "truth(air)", "mod(air)", "");
        System.out.println("-".repeat(66));
        for (Facing f : FACINGS) {
            for (double rot : new double[]{ 8.0, -8.0 }) {
                for (boolean rev : new boolean[]{ false, true }) {
                    Vec truth = truthWashDir(f, rot, rev);
                    Vec mine = modWashDir(f, rot, rev);
                    boolean ok = truth.equals(mine);
                    if (ok) pass++; else fail++;
                    System.out.printf("%-6s %-8.1f %-9s | %-12s %-12s %s%n",
                            f.name, rot, rev, truth, mine, ok ? "ok" : "MISMATCH");
                }
            }
        }

        // Idle propeller: thrust unreadable, fallback path must still be sane.
        System.out.println("-".repeat(66));
        for (Facing f : FACINGS) {
            Vec mine = modWashDir(f, 0.0, false);
            System.out.printf("%-6s idle -> %s%n", f.name, mine);
        }

        System.out.printf("%n%d passed, %d failed%n", pass, fail);
        if (fail > 0) System.exit(1);
    }
}
