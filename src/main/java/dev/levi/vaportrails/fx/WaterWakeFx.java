package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.VTConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Effect 5: water wake. A V-shaped foam trail plus bobbing stern foam when a
 * hull moves through water, scaled by speed and hull width.
 */
public final class WaterWakeFx {

    public static void tick(ClientLevel level, ShipCtx ship, RandomSource rng) {
        if (!VTConfig.WAKE_ENABLED.get()) {
            return;
        }
        if (ship.speedMs() < VTConfig.WAKE_MIN_SPEED.get()) {
            return;
        }
        double scale = Budget.emissionScale() * VTConfig.WAKE_INTENSITY.get();
        if (scale <= 0.0) {
            return;
        }

        AABB box = ship.bounds();
        // Waterline: first water surface under the ship centre, must intersect the hull.
        FxUtil.Probe probe = FxUtil.probeDown(level, ship.center().x, ship.center().y, ship.center().z,
                ship.center().y - box.minY + 1.5);
        if (probe.type() != FxUtil.SurfaceType.WATER) {
            return;
        }
        double waterY = probe.surfaceY();
        if (waterY < box.minY - 0.5) {
            return; // flying above water, not in it
        }

        Vec3 dir = ship.horizontalDir();
        if (dir == null) {
            return;
        }
        Vec3 wing = dir.cross(new Vec3(0, 1, 0)).normalize();
        double halfLen = projectedHalfExtent(box, dir);
        double halfWidth = projectedHalfExtent(box, wing);

        Vec3 bow = new Vec3(ship.center().x, waterY, ship.center().z).add(dir.scale(halfLen * 0.85));
        Vec3 stern = new Vec3(ship.center().x, waterY, ship.center().z).subtract(dir.scale(halfLen * 0.85));

        double speedFrac = Mth.clamp(ship.speedMs() / 20.0, 0.1, 1.0);
        // Kelvin wake half-angle is ~19.5 deg; the outward drift sets the V shape
        // as the ship leaves the foam behind.
        double outSpeed = Math.min(0.14, ship.velocity().horizontalDistance() * Math.tan(Math.toRadians(19.5)));
        double sideRate = (1.5 + halfWidth * 0.5) * speedFrac * 2.2 * scale;

        for (int side = -1; side <= 1; side += 2) {
            int n = FxUtil.count(rng, sideRate);
            for (int i = 0; i < n; i++) {
                Vec3 pos = bow.add(wing.scale(side * halfWidth * (0.75 + rng.nextDouble() * 0.35)))
                        .subtract(dir.scale(rng.nextDouble() * halfLen * 0.4));
                Vec3 vel = wing.scale(side * outSpeed * (0.7 + rng.nextDouble() * 0.6))
                        .subtract(dir.scale(0.01));
                FxUtil.foam(level, false, pos.x, waterY + 0.03, pos.z, vel.x, vel.z,
                        0.40f + rng.nextFloat() * 0.30f, 40 + rng.nextInt(30), 0.9f);
            }
        }

        // Bobbing foam churned up directly behind the stern.
        int churn = FxUtil.count(rng, speedFrac * 1.2 * scale);
        for (int i = 0; i < churn; i++) {
            Vec3 pos = stern.add(wing.scale((rng.nextDouble() - 0.5) * halfWidth * 1.2))
                    .subtract(dir.scale(rng.nextDouble() * 2.0));
            FxUtil.foam(level, false, pos.x, waterY + 0.03, pos.z,
                    (rng.nextDouble() - 0.5) * 0.02, (rng.nextDouble() - 0.5) * 0.02,
                    0.45f + rng.nextFloat() * 0.35f, 60 + rng.nextInt(40), 0.85f);
        }

        // Bow splash at speed.
        if (ship.speedMs() > 8.0) {
            int n = FxUtil.count(rng, speedFrac * 2.0 * scale);
            for (int i = 0; i < n; i++) {
                Vec3 pos = bow.add(wing.scale((rng.nextDouble() - 0.5) * halfWidth));
                FxUtil.vanilla(level, false, ParticleTypes.SPLASH,
                        pos.x, waterY + 0.15, pos.z,
                        dir.x * 0.2 + (rng.nextDouble() - 0.5) * 0.2,
                        0.1 + rng.nextDouble() * 0.2 * speedFrac,
                        dir.z * 0.2 + (rng.nextDouble() - 0.5) * 0.2);
            }
        }
    }

    /** Half of the box's extent along {@code axis} (axis is horizontal + unit). */
    private static double projectedHalfExtent(AABB box, Vec3 axis) {
        double hx = (box.maxX - box.minX) * 0.5;
        double hz = (box.maxZ - box.minZ) * 0.5;
        return Math.abs(axis.x) * hx + Math.abs(axis.z) * hz;
    }

    private WaterWakeFx() {}
}
