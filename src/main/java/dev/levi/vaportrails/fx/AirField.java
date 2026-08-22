package dev.levi.vaportrails.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A shared, client-side airflow field that every Vapor Trails particle drifts
 * through.
 *
 * Instead of each emitter baking a fixed velocity into its particles and then
 * forgetting about them, propellers publish their slipstream here as cone
 * shaped "jets", and living particles sample the field every tick. Prop wash
 * therefore blows nearby vapor, dust and foam around, a ship's slipstream drags
 * its own wingtip trails, and everything settles into the ambient breeze once
 * it escapes.
 *
 * Deliberately allocation free: jets live in flat parallel arrays, and sampling
 * writes into static scratch fields. Everything here is client-thread only.
 */
public final class AirField {

    /** Hard cap on simultaneous jets; beyond this the weakest are dropped. */
    private static final int MAX_JETS = 64;

    private static final double[] PX = new double[MAX_JETS];
    private static final double[] PY = new double[MAX_JETS];
    private static final double[] PZ = new double[MAX_JETS];
    private static final double[] DX = new double[MAX_JETS];
    private static final double[] DY = new double[MAX_JETS];
    private static final double[] DZ = new double[MAX_JETS];
    private static final double[] STRENGTH = new double[MAX_JETS];
    private static final double[] RADIUS = new double[MAX_JETS];
    private static final double[] LENGTH = new double[MAX_JETS];
    private static final double[] SWIRL = new double[MAX_JETS];

    private static int count;
    private static boolean enabled = true;

    private static double breezeX;
    private static double breezeZ;

    /** Scratch output of the last {@link #sample} call. */
    private static double outX;
    private static double outY;
    private static double outZ;

    /** Clear last tick's jets and refresh the ambient breeze. */
    public static void beginTick(ClientLevel level) {
        count = 0;
        enabled = dev.levi.vaportrails.VTConfig.AIRFLOW_ENABLED.get();
        if (!enabled) {
            breezeX = 0.0;
            breezeZ = 0.0;
            return;
        }
        // A slow wandering breeze so long-lived contrails disperse instead of
        // hanging in perfectly straight lines. Picks up in rain and storms.
        double t = level.getGameTime() * 0.0015;
        double gust = level.isThundering() ? 3.0 : level.isRaining() ? 1.8 : 1.0;
        breezeX = Math.sin(t) * 0.0035 * gust;
        breezeZ = Math.cos(t * 0.77) * 0.0035 * gust;
    }

    /**
     * Publish a cone of moving air.
     *
     * @param pos      cone apex (the propeller disc)
     * @param dir      unit direction the air travels
     * @param strength peak air speed at the apex, blocks per tick
     * @param radius   cone radius at the apex
     * @param length   how far downstream the cone reaches
     * @param swirl    tangential spin around the cone axis, blocks per tick
     */
    public static void addJet(Vec3 pos, Vec3 dir, double strength, double radius,
                              double length, double swirl) {
        if (!enabled || strength <= 0.0 || length <= 0.0) {
            return;
        }
        int slot = count;
        if (slot >= MAX_JETS) {
            // Full: replace the weakest jet if this one beats it.
            int weakest = 0;
            for (int i = 1; i < MAX_JETS; i++) {
                if (STRENGTH[i] < STRENGTH[weakest]) {
                    weakest = i;
                }
            }
            if (STRENGTH[weakest] >= strength) {
                return;
            }
            slot = weakest;
        } else {
            count++;
        }
        PX[slot] = pos.x;
        PY[slot] = pos.y;
        PZ[slot] = pos.z;
        DX[slot] = dir.x;
        DY[slot] = dir.y;
        DZ[slot] = dir.z;
        STRENGTH[slot] = strength;
        RADIUS[slot] = radius;
        LENGTH[slot] = length;
        SWIRL[slot] = swirl;
    }

    /**
     * Evaluate the airflow at a world position. Read the result with
     * {@link #outX()}, {@link #outY()} and {@link #outZ()}.
     */
    public static void sample(double x, double y, double z) {
        double ax = breezeX;
        double ay = 0.0;
        double az = breezeZ;

        for (int i = 0; i < count; i++) {
            double vx = x - PX[i];
            double vy = y - PY[i];
            double vz = z - PZ[i];

            // Distance along the cone axis; behind the disc means no influence.
            double axial = vx * DX[i] + vy * DY[i] + vz * DZ[i];
            double len = LENGTH[i];
            if (axial < 0.0 || axial > len) {
                continue;
            }

            // Perpendicular offset from the axis.
            double rx = vx - DX[i] * axial;
            double ry = vy - DY[i] * axial;
            double rz = vz - DZ[i] * axial;
            double radialSq = rx * rx + ry * ry + rz * rz;

            // The cone widens as it travels and slows as it spreads.
            double spread = RADIUS[i] * (1.0 + 1.6 * (axial / len));
            if (radialSq > spread * spread) {
                continue;
            }

            double radial = Math.sqrt(radialSq);
            double axialFall = 1.0 - (axial / len);
            double radialFall = 1.0 - (radial / spread);
            double power = STRENGTH[i] * axialFall * radialFall * radialFall;

            ax += DX[i] * power;
            ay += DY[i] * power;
            az += DZ[i] * power;

            // Tangential swirl makes wash curl rather than blow straight.
            if (SWIRL[i] != 0.0 && radial > 1.0e-4) {
                double inv = 1.0 / radial;
                double nx = rx * inv;
                double ny = ry * inv;
                double nz = rz * inv;
                // tangent = dir x radialUnit
                double tx = DY[i] * nz - DZ[i] * ny;
                double ty = DZ[i] * nx - DX[i] * nz;
                double tz = DX[i] * ny - DY[i] * nx;
                double s = SWIRL[i] * axialFall * radialFall;
                ax += tx * s;
                ay += ty * s;
                az += tz * s;
            }
        }

        outX = ax;
        outY = ay;
        outZ = az;
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

    /** True when there is nothing but ambient breeze to sample. */
    public static boolean idle() {
        return count == 0 && Math.abs(breezeX) < 1.0e-5 && Math.abs(breezeZ) < 1.0e-5;
    }

    public static int jetCount() {
        return count;
    }

    /** Blend a particle's velocity toward the local airflow. */
    public static double drift(double current, double air, double responsiveness) {
        return current + (air - current) * Mth.clamp(responsiveness, 0.0, 1.0);
    }

    public static void reset() {
        count = 0;
        breezeX = 0.0;
        breezeZ = 0.0;
    }

    private AirField() {}
}
