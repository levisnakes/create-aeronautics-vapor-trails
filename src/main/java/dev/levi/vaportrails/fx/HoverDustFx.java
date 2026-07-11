package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.VTConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Effect 4: hover dust. A ship hovering or flying low kicks block-appropriate
 * dust/debris off the ground beneath it (or spray rings off water). Areas
 * under an active propeller are excluded - that's PropellerFx's job (2c),
 * so propeller wash reads as its own effect.
 */
public final class HoverDustFx {

    public static void tick(ClientLevel level, ShipCtx ship, RandomSource rng) {
        if (!VTConfig.HOVER_ENABLED.get()) {
            return;
        }
        double scale = Budget.emissionScale() * VTConfig.HOVER_INTENSITY.get();
        if (scale <= 0.0) {
            return;
        }

        boolean propRunning = ship.props().stream()
                .anyMatch(p -> p.active() && Math.abs(p.rpm()) > 8.0);
        // A parked ship resting on the ground shouldn't dust forever: require
        // movement or running machinery ("hovering" implies powered flight).
        if (ship.speedMs() < 0.5 && !propRunning) {
            return;
        }

        double maxHeight = VTConfig.HOVER_MAX_HEIGHT.get();
        AABB box = ship.bounds();
        double area = (box.maxX - box.minX) * (box.maxZ - box.minZ);
        int samples = Mth.clamp((int) Math.ceil(area / 48.0), 1, 5);

        for (int s = 0; s < samples; s++) {
            double px = Mth.lerp(0.1 + rng.nextDouble() * 0.8, box.minX, box.maxX);
            double pz = Mth.lerp(0.1 + rng.nextDouble() * 0.8, box.minZ, box.maxZ);

            // Leave the area under running propellers to the prop-wash effect.
            if (nearActiveProp(ship, px, pz)) {
                continue;
            }

            FxUtil.Probe probe = FxUtil.probeDown(level, px, box.minY + 0.5, pz, maxHeight + 1.0);
            if (probe.type() == FxUtil.SurfaceType.NONE) {
                continue;
            }
            double clearance = box.minY - probe.surfaceY();
            if (clearance > maxHeight) {
                continue;
            }
            double strength = Mth.clamp(1.0 - clearance / maxHeight, 0.1, 1.0);

            Vec3 out = new Vec3(px - ship.center().x, 0, pz - ship.center().z);
            out = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : out.normalize();

            int n = FxUtil.count(rng, (1.2 + 2.0 * strength) * scale);
            for (int i = 0; i < n; i++) {
                double ox = out.x * (0.08 + rng.nextDouble() * 0.12 * strength);
                double oz = out.z * (0.08 + rng.nextDouble() * 0.12 * strength);
                if (probe.type() == FxUtil.SurfaceType.WATER) {
                    FxUtil.vanilla(level, false, ParticleTypes.SPLASH,
                            px, probe.surfaceY() + 0.1, pz, ox * 2.0, 0.08 + 0.15 * strength, oz * 2.0);
                    FxUtil.foam(level, false, px, probe.surfaceY() + 0.02, pz, ox * 0.5, oz * 0.5,
                            0.22f + rng.nextFloat() * 0.15f, 25 + rng.nextInt(25), 0.75f);
                } else {
                    FxUtil.vanilla(level, false, new BlockParticleOption(ParticleTypes.BLOCK, probe.state()),
                            px + (rng.nextDouble() - 0.5), probe.surfaceY() + 0.15, pz + (rng.nextDouble() - 0.5),
                            ox * 4.0, 0.4 * strength, oz * 4.0);
                }
            }
        }
    }

    private static boolean nearActiveProp(ShipCtx ship, double x, double z) {
        for (ShipCtx.PropCtx p : ship.props()) {
            if (!p.active() || Math.abs(p.rpm()) < 8.0) {
                continue;
            }
            double r = Math.max(1.5, p.radius() * 1.5);
            double dx = x - p.pos().x;
            double dz = z - p.pos().z;
            if (dx * dx + dz * dz < r * r) {
                return true;
            }
        }
        return false;
    }

    private HoverDustFx() {}
}
