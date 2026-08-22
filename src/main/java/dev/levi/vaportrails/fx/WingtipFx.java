package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.VTConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Effect 1: wingtip vapor trails. White condensation streaks from the two
 * bounding-box extremities perpendicular to the velocity, fading over 3-6 s.
 */
public final class WingtipFx {

    /** Reused so the per-tick extremity queries allocate nothing. */
    private static final double[] TIP_SCRATCH = new double[3];

    public static void tick(ClientLevel level, ShipCtx ship, RandomSource rng) {
        if (!VTConfig.WINGTIP_ENABLED.get()) {
            return;
        }
        double minSpeed = VTConfig.WINGTIP_MIN_SPEED.get();
        if (ship.speedMs() < minSpeed) {
            return;
        }
        double scale = Budget.emissionScale() * VTConfig.WINGTIP_INTENSITY.get();
        if (scale <= 0.0) {
            return;
        }

        Vec3 dir = ship.velocity().normalize();
        Vec3 tipA;
        Vec3 tipB;

        if (ship.hasShape()) {
            // Wings belong to the ship, not to the world: find the span axis in
            // SHIP space so a banking aircraft still trails from its real tips.
            Vec3 localDir = ship.frame().dirToLocal(dir);
            double wx = -localDir.z;
            double wz = localDir.x;
            double len = Math.sqrt(wx * wx + wz * wz);
            if (len < 1.0e-4) {
                // Travelling straight along the ship's own vertical axis.
                wx = 1.0;
                wz = 0.0;
            } else {
                wx /= len;
                wz /= len;
            }
            double[] tip = TIP_SCRATCH;
            ship.shape().extreme(wx, wz, tip);
            tipA = ship.frame().toWorld(tip[0], tip[1], tip[2]);
            ship.shape().extreme(-wx, -wz, tip);
            tipB = ship.frame().toWorld(tip[0], tip[1], tip[2]);
        } else {
            // No silhouette yet: fall back to the (inflated) bounding box.
            Vec3 wing = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0)
                    : dir.cross(new Vec3(0, 1, 0)).normalize();
            tipA = extremeCorner(ship.bounds(), ship.center(), wing, 1.0);
            tipB = extremeCorner(ship.bounds(), ship.center(), wing, -1.0);
        }

        double density = Mth.clamp((ship.speedMs() - minSpeed) / Math.max(minSpeed, 1.0), 0.0, 1.0);
        double humid = PropellerFx.humidityBoost(level, ship.center().y);
        double rate = (2.5 + 3.0 * density) * humid * scale;

        emitTip(level, ship, rng, tipA, dir, rate);
        emitTip(level, ship, rng, tipB, dir, rate);
    }

    /**
     * Fallback for ships with no silhouette yet: the bounding-box corner
     * farthest along {@code axis * sign}, pulled 10% toward centre.
     */
    private static Vec3 extremeCorner(AABB box, Vec3 center, Vec3 axis, double sign) {
        Vec3 best = null;
        double bestD = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 c = new Vec3(
                    (i & 1) == 0 ? box.minX : box.maxX,
                    (i & 2) == 0 ? box.minY : box.maxY,
                    (i & 4) == 0 ? box.minZ : box.maxZ);
            double d = c.subtract(center).dot(axis) * sign;
            if (d > bestD) {
                bestD = d;
                best = c;
            }
        }
        return best.add(center.subtract(best).scale(0.10));
    }

    private static void emitTip(ClientLevel level, ShipCtx ship, RandomSource rng,
                                Vec3 tip, Vec3 dir, double rate) {
        int n = FxUtil.count(rng, rate);
        for (int i = 0; i < n; i++) {
            // Slightly aft of the tip, with a touch of jitter so the streak has body.
            double jx = (rng.nextDouble() - 0.5) * 0.5;
            double jy = (rng.nextDouble() - 0.5) * 0.5;
            double jz = (rng.nextDouble() - 0.5) * 0.5;
            Vec3 pos = tip.subtract(dir.scale(0.4 + rng.nextDouble() * 0.8));
            // The vapor mostly stays put in the air; the ship leaves it behind.
            FxUtil.vapor(level, false,
                    pos.x + jx, pos.y + jy, pos.z + jz,
                    ship.velocity().x * 0.12, ship.velocity().y * 0.12, ship.velocity().z * 0.12,
                    0.65f + rng.nextFloat() * 0.4f,
                    60 + rng.nextInt(60),                     // 3-6 seconds
                    0.65f, 2.2f);
        }
    }

    private WingtipFx() {}
}
