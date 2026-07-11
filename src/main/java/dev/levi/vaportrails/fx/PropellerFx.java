package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.VTConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The headline feature: per-propeller effects.
 *
 * 2a. Blade-tip vapor - condensation puffs at the blade-tip radius, phased to
 *     the blade pass so the disc reads as a flickering ring/halo.
 * 2b. Prop wash - a cone of faint air swirls streaming out behind the disc.
 * 2c. Ground/water disturbance - dust ring or spray directly under a running
 *     disc near the surface, distinct from generic hover dust.
 * 2d. Startup/shutdown puff - a punchy smoke/vapor burst on spin-up/down.
 */
public final class PropellerFx {

    /** RPM above which a propeller counts as "spinning" for start/stop bursts. */
    private static final double SPIN_RPM = 8.0;
    /** Create rotation speeds top out at 256 RPM. */
    private static final double MAX_RPM = 256.0;

    public static void tick(ClientLevel level, ShipCtx ship, RandomSource rng) {
        double scale = Budget.emissionScale() * VTConfig.PROP_INTENSITY.get();
        if (scale <= 0.0) {
            return;
        }
        for (ShipCtx.PropCtx prop : ship.props()) {
            tickProp(level, ship, prop, rng, scale);
        }
    }

    private static void tickProp(ClientLevel level, ShipCtx ship, ShipCtx.PropCtx prop,
                                 RandomSource rng, double scale) {
        PropFxState st = prop.st();
        double rpm = Math.abs(prop.rpm());
        boolean spinning = rpm >= SPIN_RPM && prop.active();

        // 2d - startup/shutdown burst on the spin transition.
        if (st.spinInit && spinning != st.wasSpinning && VTConfig.PROP_STARTUP_ENABLED.get()) {
            burst(level, prop, rng, scale, spinning);
        }
        st.wasSpinning = spinning;
        st.spinInit = true;
        st.prevRpm = prop.rpm();

        if (!spinning) {
            return;
        }

        // Blade phase: Create visuals advance speed * 0.3 degrees per tick.
        st.phase = (st.phase + Math.toRadians(prop.rpm() * 0.3)) % Mth.TWO_PI;

        Vec3 u = FxUtil.perpendicular(prop.axis());
        Vec3 w = prop.axis().cross(u).normalize();

        if (VTConfig.PROP_TIP_ENABLED.get() && rpm >= VTConfig.PROP_TIP_MIN_RPM.get()) {
            bladeTips(level, ship, prop, rng, scale, u, w);
        }
        if (VTConfig.PROP_WASH_ENABLED.get() && rpm >= VTConfig.PROP_WASH_MIN_RPM.get()) {
            wash(level, ship, prop, rng, scale, u, w);
        }
        if (VTConfig.PROP_GROUND_ENABLED.get() && rpm >= VTConfig.PROP_WASH_MIN_RPM.get()) {
            groundDisturbance(level, prop, rng, scale, u, w);
        }
    }

    // ---- 2a blade-tip vapor ----

    private static void bladeTips(ClientLevel level, ShipCtx ship, ShipCtx.PropCtx prop,
                                  RandomSource rng, double scale, Vec3 u, Vec3 w) {
        double rpm = Math.abs(prop.rpm());
        double thr = VTConfig.PROP_TIP_MIN_RPM.get();
        double density = Mth.clamp((rpm - thr) / Math.max(1.0, MAX_RPM - thr), 0.0, 1.0);
        double humid = humidityBoost(level, prop.pos().y);
        int blades = VTConfig.PROP_BLADES.get();
        double perBlade = (0.9 + 1.1 * density) * humid * scale;

        double tipSpeed = 0.04 + 0.10 * (rpm / MAX_RPM);
        for (int k = 0; k < blades; k++) {
            int n = FxUtil.count(rng, perBlade);
            if (n == 0) {
                continue;
            }
            double angle = prop.st().phase + k * (Mth.TWO_PI / blades);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 radial = u.scale(cos).add(w.scale(sin));
            Vec3 tip = prop.pos().add(radial.scale(prop.radius()));
            // Tangential kick in the direction of rotation.
            Vec3 tangent = prop.axis().cross(radial).scale(Math.signum(prop.rpm()) * tipSpeed);
            for (int i = 0; i < n; i++) {
                double jx = (rng.nextDouble() - 0.5) * 0.15;
                double jy = (rng.nextDouble() - 0.5) * 0.15;
                double jz = (rng.nextDouble() - 0.5) * 0.15;
                FxUtil.vapor(level, true,
                        tip.x + jx, tip.y + jy, tip.z + jz,
                        tangent.x + ship.velocity().x * 0.15,
                        tangent.y + ship.velocity().y * 0.15,
                        tangent.z + ship.velocity().z * 0.15,
                        0.55f + rng.nextFloat() * 0.25f, 10 + rng.nextInt(8),
                        0.8f, 1.6f);
            }
        }
    }

    // ---- 2b prop wash ----

    private static void wash(ClientLevel level, ShipCtx ship, ShipCtx.PropCtx prop,
                             RandomSource rng, double scale, Vec3 u, Vec3 w) {
        double rpm = Math.abs(prop.rpm());
        Vec3 washDir = prop.axis().scale(-thrustSign(prop));
        double washSpeed = 0.12 + 0.40 * (rpm / MAX_RPM);
        // The wash cone also stretches with how fast the ship itself moves.
        double shipBoost = Mth.clamp(ship.speedMs() / 30.0, 0.0, 1.0);
        double rate = (2.5 + 3.0 * (rpm / MAX_RPM) + shipBoost * 2.0) * scale;
        int n = FxUtil.count(rng, rate);
        for (int i = 0; i < n; i++) {
            double angle = rng.nextDouble() * Mth.TWO_PI;
            double r = prop.radius() * (0.25 + 0.70 * Math.sqrt(rng.nextDouble()));
            Vec3 offset = u.scale(Math.cos(angle) * r).add(w.scale(Math.sin(angle) * r));
            Vec3 start = prop.pos().add(offset).add(washDir.scale(0.4 + rng.nextDouble()));
            // Slight outward flare gives the stream a cone shape.
            Vec3 vel = washDir.scale(washSpeed)
                    .add(offset.normalize().scale(0.015))
                    .add(ship.velocity().scale(0.35));
            FxUtil.wash(level, true, start.x, start.y, start.z, vel.x, vel.y, vel.z,
                    0.55f + (float) (r / Math.max(0.5, prop.radius())) * 0.35f,
                    18 + rng.nextInt(16), 0.45f);
        }
    }

    // ---- 2c ground/water disturbance ----

    private static void groundDisturbance(ClientLevel level, ShipCtx.PropCtx prop,
                                          RandomSource rng, double scale, Vec3 u, Vec3 w) {
        double range = VTConfig.PROP_GROUND_RANGE.get();
        FxUtil.Probe probe = FxUtil.probeDown(level, prop.pos().x, prop.pos().y - 0.2, prop.pos().z, range);
        if (probe.type() == FxUtil.SurfaceType.NONE) {
            return;
        }
        double depth = prop.pos().y - probe.surfaceY();
        double strength = Mth.clamp(1.0 - depth / range, 0.05, 1.0)
                * (Math.abs(prop.rpm()) / MAX_RPM);
        double ringR = Math.max(1.0, prop.radius());
        int n = FxUtil.count(rng, (4.0 + 8.0 * strength) * scale);
        for (int i = 0; i < n; i++) {
            double angle = rng.nextDouble() * Mth.TWO_PI;
            double r = ringR * (0.7 + rng.nextDouble() * 0.6);
            // The ring sits directly under the disc, flattened onto the surface.
            double px = prop.pos().x + Math.cos(angle) * r;
            double pz = prop.pos().z + Math.sin(angle) * r;
            double outX = Math.cos(angle) * (0.10 + 0.15 * strength);
            double outZ = Math.sin(angle) * (0.10 + 0.15 * strength);
            if (probe.type() == FxUtil.SurfaceType.WATER) {
                FxUtil.vanilla(level, true, ParticleTypes.SPLASH,
                        px, probe.surfaceY() + 0.1, pz, outX * 2.0, 0.12 + 0.25 * strength, outZ * 2.0);
                FxUtil.foam(level, true, px, probe.surfaceY() + 0.02, pz, outX * 0.6, outZ * 0.6,
                        0.25f + rng.nextFloat() * 0.15f, 25 + rng.nextInt(20), 0.8f);
            } else {
                FxUtil.vanilla(level, true, new BlockParticleOption(ParticleTypes.BLOCK, probe.state()),
                        px, probe.surfaceY() + 0.15, pz, outX * 4.0, 0.8 * strength, outZ * 4.0);
                if (rng.nextFloat() < 0.5f) {
                    FxUtil.wash(level, true, px, probe.surfaceY() + 0.3, pz, outX, 0.04, outZ,
                            0.6f, 16 + rng.nextInt(10), 0.4f);
                }
            }
        }
    }

    // ---- 2d startup/shutdown puff ----

    private static void burst(ClientLevel level, ShipCtx.PropCtx prop, RandomSource rng,
                              double scale, boolean startingUp) {
        Vec3 u = FxUtil.perpendicular(prop.axis());
        Vec3 w = prop.axis().cross(u).normalize();
        int n = FxUtil.count(rng, (startingUp ? 30.0 : 16.0) * scale);
        double r0 = Math.max(0.4, prop.radius() * 0.45);
        for (int i = 0; i < n; i++) {
            double angle = rng.nextDouble() * Mth.TWO_PI;
            Vec3 radial = u.scale(Math.cos(angle)).add(w.scale(Math.sin(angle)));
            Vec3 pos = prop.pos().add(radial.scale(r0 * rng.nextDouble()));
            Vec3 vel = radial.scale(0.06 + rng.nextDouble() * 0.12)
                    .add(prop.axis().scale((rng.nextDouble() - 0.5) * 0.06));
            if (i % 3 == 0) {
                // Denser grey smoke core: engines cough when they catch.
                FxUtil.smokeVapor(level, true, 0.35f + rng.nextFloat() * 0.1f,
                        pos.x, pos.y, pos.z, vel.x, vel.y + 0.02, vel.z,
                        0.8f + rng.nextFloat() * 0.4f, 28 + rng.nextInt(16), 0.8f, 1.8f);
            } else {
                FxUtil.vapor(level, true, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z,
                        0.7f + rng.nextFloat() * 0.5f, 20 + rng.nextInt(14), 0.75f, 2.0f);
            }
        }
    }

    // ---- helpers ----

    private static double thrustSign(ShipCtx.PropCtx prop) {
        if (Math.abs(prop.thrust()) > 1.0e-3) {
            return Math.signum(prop.thrust());
        }
        if (Math.abs(prop.rpm()) > 1.0e-3) {
            return Math.signum(prop.rpm());
        }
        return 1.0;
    }

    /** Vapor forms far more readily in rain or near the cloud layer. */
    static double humidityBoost(ClientLevel level, double y) {
        double boost = level.isRaining() ? 1.6 : 1.0;
        float cloudY = level.effects().getCloudHeight();
        if (!Float.isNaN(cloudY) && Math.abs(y - (cloudY + 2.0)) < 12.0) {
            boost *= 2.0;
        }
        return boost;
    }

    private PropellerFx() {}
}
