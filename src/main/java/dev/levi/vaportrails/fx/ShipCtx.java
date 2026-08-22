package dev.levi.vaportrails.fx;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Per-tick, world-space snapshot of one Sable ship. Everything is expressed
 * in vanilla types so the effect classes never touch Sable/Create classes.
 *
 * @param velocity        ship velocity in blocks per tick
 * @param angularVelocity  spin as an axis-angle vector, radians per tick
 * @param speedMs          |velocity| in metres (blocks) per second
 * @param bounds   world-axis-aligned bounds; a loose fallback, inflated when
 *                 the ship is rotated. Prefer {@code shape} when it is present.
 * @param frame    ship-local to world transform
 * @param shape    the real block silhouette in ship-local space, or null while
 *                 the scan is still running or the ship is too large to scan
 */
public record ShipCtx(UUID id, Vec3 center, Vec3 velocity, Vec3 angularVelocity,
                      double speedMs, AABB bounds,
                      ShipFrame frame, HullShape shape,
                      List<PropCtx> props, List<EngineCtx> engines, ShipFxState st) {

    /**
     * Velocity of a specific point on the ship, in blocks per tick.
     *
     * A rotating hull moves its extremities faster than its centre of mass
     * (v + omega x r), which is why a banking aircraft streams vapor from the
     * rising wingtip. Effects anchored away from the centre should use this
     * rather than {@link #velocity()}.
     */
    public Vec3 velocityAt(Vec3 worldPoint) {
        if (angularVelocity.lengthSqr() < 1.0e-12) {
            return velocity;
        }
        return velocity.add(angularVelocity.cross(worldPoint.subtract(center)));
    }

    /** Speed of a specific point in metres (blocks) per second. */
    public double speedMsAt(Vec3 worldPoint) {
        return velocityAt(worldPoint).length() * 20.0;
    }

    /** True when the real hull silhouette is available. */
    public boolean hasShape() {
        return shape != null && shape.valid();
    }

    /** Normalized horizontal travel direction, or null when (nearly) stationary/vertical. */
    public Vec3 horizontalDir() {
        Vec3 h = new Vec3(velocity.x, 0, velocity.z);
        double len = h.length();
        return len < 1.0e-4 ? null : h.scale(1.0 / len);
    }

    /**
     * @param pos    hub/disc centre in world space
     * @param axis   world-space rotation axis (unit, pointing along block facing)
     * @param radius blade-tip radius in blocks
     * @param rpm       signed Create rotation speed
     * @param thrust    signed thrust magnitude (0 when unknown)
     * @param thrustDir  world-space unit vector the propeller pushes the ship
     *                   toward; air is driven the opposite way
     * @param bladeAngle the propeller's real rendered blade angle in radians,
     *                   or NaN when Aeronautics could not supply one
     */
    public record PropCtx(Vec3 pos, Vec3 axis, double radius, double rpm, double thrust,
                          boolean active, Vec3 thrustDir, double bladeAngle, PropFxState st) {

        /** True when the real blade angle is available. */
        public boolean hasBladeAngle() {
            return !Double.isNaN(bladeAngle);
        }


        /** Direction the propeller drives air, i.e. where the wash goes. */
        public Vec3 washDir() {
            return thrustDir.scale(-1.0);
        }
    }

    public record EngineCtx(Vec3 pos, boolean running, EngineFxState st) {}
}
