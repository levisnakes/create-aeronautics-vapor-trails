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
        // Perpendicular "wing" axis; for near-vertical flight fall back to world X.
        Vec3 wing = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0)
                : dir.cross(new Vec3(0, 1, 0)).normalize();

        Vec3 tipA = extremeCorner(ship.bounds(), ship.center(), wing, 1.0);
        Vec3 tipB = extremeCorner(ship.bounds(), ship.center(), wing, -1.0);

        double density = Mth.clamp((ship.speedMs() - minSpeed) / Math.max(minSpeed, 1.0), 0.0, 1.0);
        double humid = PropellerFx.humidityBoost(level, ship.center().y);
        double rate = (0.8 + 1.8 * density) * humid * scale;

        emitTip(level, ship, rng, tipA, dir, rate);
        emitTip(level, ship, rng, tipB, dir, rate);
    }

    /** The bounding-box corner farthest along {@code axis * sign}, pulled 10% toward centre. */
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
                    0.5f + rng.nextFloat() * 0.4f,
                    60 + rng.nextInt(60),                     // 3-6 seconds
                    0.35f, 2.0f);
        }
    }

    private WingtipFx() {}
}
