package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.VTConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Effect 8: wake turbulence.
 *
 * Two halves. Shedding drops counter-rotating vortex cores from a fast ship's
 * wingtips into {@link WakeField}, where they persist long after the visible
 * condensation has faded. Revealing does the opposite: when a ship flies into
 * air that an earlier flight has already stirred up, the disturbance is drawn
 * back out as ragged vapour torn along the local flow.
 *
 * The reveal is what makes the feature discoverable. Without it the wake would
 * only ever show up as a subtle nudge on particles that happened to be nearby,
 * and most players would never notice it existed.
 */
public final class WakeTurbulenceFx {

    /** Ticks between shed pairs. Wider spacing means fewer, longer cores. */
    private static final int SHED_INTERVAL = 4;
    /** Induced flow below this is not worth drawing, in blocks per tick. */
    private static final double REVEAL_FLOOR = 0.035;

    /**
     * Shed a vortex pair from the wingtips. Called by {@link WingtipFx}, which
     * has already worked out where the real tips are.
     */
    public static void shed(ShipCtx ship, Vec3 tipA, Vec3 tipB, Vec3 dir) {
        if (!VTConfig.WAKE_TURB_ENABLED.get()) {
            return;
        }
        ShipFxState st = ship.st();
        if (--st.wakeShedIn > 0) {
            return;
        }
        st.wakeShedIn = SHED_INTERVAL;

        double span = tipA.distanceTo(tipB);
        if (span < 1.0) {
            return;
        }
        // Circulation grows with how hard the wing is working: span x airspeed.
        double base = Mth.clamp(ship.velocity().length() * span * 0.010, 0.0, 0.8)
                * VTConfig.WAKE_TURB_STRENGTH.get();
        if (base <= 0.001) {
            return;
        }
        int ownerId = ship.id().hashCode() | 1; // never 0; 0 means "no owner"
        int life = WakeField.lifeTicks();

        // The two tips counter-rotate, which is what makes the pair pull air
        // down between them the way a real wake does.
        st.lastShedA = shedTip(tipA, dir, base, span, st.lastShedA, ownerId, life);
        st.lastShedB = shedTip(tipB, dir, -base, span, st.lastShedB, ownerId, life);
    }

    private static Vec3 shedTip(Vec3 tip, Vec3 dir, double circulation, double span,
                                Vec3 previous, int ownerId, int life) {
        double spacing = previous == null ? 3.0 : previous.distanceTo(tip);
        // A ship that has barely moved should not stack cores on one spot.
        if (spacing < 0.75) {
            return previous;
        }
        WakeField.deposit(tip, dir, circulation, span, Math.min(spacing, 12.0), ownerId, life);
        return tip;
    }

    /**
     * Draw out the turbulence a ship is currently flying through, and let it
     * shake the ship's own trails.
     */
    public static void tick(ClientLevel level, ShipCtx ship, RandomSource rng) {
        ShipFxState st = ship.st();
        if (!VTConfig.WAKE_TURB_ENABLED.get() || !VTConfig.WAKE_TURB_REVEAL.get()
                || WakeField.idle()) {
            st.turbulence = 0.0;
            return;
        }
        double scale = Budget.emissionScale();
        if (scale <= 0.0) {
            return;
        }

        int ownerId = ship.id().hashCode() | 1;
        double raw = WakeField.disturbance(ship.center().x, ship.center().y, ship.center().z, ownerId);
        // Smooth it so entering and leaving a wake ramps rather than flickers.
        st.turbulence += (raw - st.turbulence) * 0.25;
        if (st.turbulence < REVEAL_FLOOR) {
            return;
        }

        double intensity = Mth.clamp((st.turbulence - REVEAL_FLOOR) / 0.15, 0.0, 1.0);
        double girth = ship.hasShape()
                ? Math.max(2.0, ship.shape().halfExtent(1, 0))
                : Math.max(2.0, (ship.bounds().maxX - ship.bounds().minX) * 0.5);

        int n = FxUtil.count(rng, (2.0 + 6.0 * intensity) * scale);
        for (int i = 0; i < n; i++) {
            // Scatter through the volume the hull is passing through.
            Vec3 pos = ship.center().add(
                    (rng.nextDouble() - 0.5) * girth * 2.2,
                    (rng.nextDouble() - 0.5) * girth * 1.2,
                    (rng.nextDouble() - 0.5) * girth * 2.2);
            WakeField.sample(pos.x, pos.y, pos.z, ownerId);
            double vx = WakeField.outX();
            double vy = WakeField.outY();
            double vz = WakeField.outZ();
            if (vx * vx + vy * vy + vz * vz < REVEAL_FLOOR * REVEAL_FLOOR * 0.25) {
                continue;
            }
            // Ragged and short-lived: this is disturbed air being made visible,
            // not a new trail of its own.
            FxUtil.wash(level, false, pos.x, pos.y, pos.z,
                    vx + ship.velocity().x * 0.2,
                    vy + ship.velocity().y * 0.2,
                    vz + ship.velocity().z * 0.2,
                    0.4f + rng.nextFloat() * 0.5f,
                    12 + rng.nextInt(10),
                    (float) (0.18 + 0.22 * intensity));
        }
    }

    private WakeTurbulenceFx() {}
}
