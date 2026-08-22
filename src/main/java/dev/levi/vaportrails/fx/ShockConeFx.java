package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.VTConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Effect 7: transonic shock cone.
 *
 * Past a high speed threshold a disc of condensation forms around the hull and
 * is dragged along with it, the way a real aircraft pulls a vapour cone when
 * the pressure drop behind its shock front pushes humid air below its dew
 * point. The ring rides just behind the widest part of the ship, tightens as
 * speed climbs, and shreds into the slipstream.
 */
public final class ShockConeFx {

    public static void tick(ClientLevel level, ShipCtx ship, RandomSource rng) {
        if (!VTConfig.SHOCK_ENABLED.get()) {
            return;
        }
        double threshold = VTConfig.SHOCK_MIN_SPEED.get();
        if (ship.speedMs() < threshold) {
            return;
        }
        double scale = Budget.emissionScale() * VTConfig.SHOCK_INTENSITY.get();
        if (scale <= 0.0) {
            return;
        }

        Vec3 dir = ship.velocity().normalize();
        Vec3 u = FxUtil.perpendicular(dir);
        Vec3 w = dir.cross(u).normalize();

        AABB box = ship.bounds();
        double halfLength = projectedHalfExtent(box, dir);
        double girth = Math.max(1.5, averageGirth(box, u, w));

        // Faster means a tighter, further-back cone.
        double over = Mth.clamp((ship.speedMs() - threshold) / Math.max(threshold, 1.0), 0.0, 1.5);
        double ringR = girth * (1.05 - 0.25 * Mth.clamp(over, 0.0, 1.0));
        double standoff = halfLength * (0.15 + 0.35 * Mth.clamp(over, 0.0, 1.0));
        Vec3 centre = ship.center().subtract(dir.scale(standoff));

        double humid = PropellerFx.humidityBoost(level, ship.center().y);
        int n = FxUtil.count(rng, (6.0 + 10.0 * over) * humid * scale);
        for (int i = 0; i < n; i++) {
            double angle = rng.nextDouble() * Mth.TWO_PI;
            double r = ringR * (0.88 + rng.nextDouble() * 0.22);
            Vec3 radial = u.scale(Math.cos(angle)).add(w.scale(Math.sin(angle)));
            Vec3 pos = centre.add(radial.scale(r))
                    .subtract(dir.scale(rng.nextDouble() * girth * 0.5));
            // The sheet is swept back and outward as the ship tears past it.
            Vec3 vel = ship.velocity().scale(0.55)
                    .add(radial.scale(0.02 + rng.nextDouble() * 0.05))
                    .subtract(dir.scale(0.05));
            FxUtil.vapor(level, false, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z,
                    (float) (girth * 0.22) + rng.nextFloat() * 0.5f,
                    10 + rng.nextInt(10), 0.5f, 2.6f);
        }
    }

    private static double projectedHalfExtent(AABB box, Vec3 axis) {
        return Math.abs(axis.x) * (box.maxX - box.minX) * 0.5
                + Math.abs(axis.y) * (box.maxY - box.minY) * 0.5
                + Math.abs(axis.z) * (box.maxZ - box.minZ) * 0.5;
    }

    /** Mean hull radius across the two axes perpendicular to travel. */
    private static double averageGirth(AABB box, Vec3 u, Vec3 w) {
        return (projectedHalfExtent(box, u) + projectedHalfExtent(box, w)) * 0.5;
    }

    private ShockConeFx() {}
}
