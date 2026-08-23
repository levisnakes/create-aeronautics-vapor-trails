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
 * Effect 9: assembly and disassembly.
 *
 * Every other effect in this mod only exists once a ship is already flying, but
 * the one moment every airship player is guaranteed to experience - and to be
 * standing right next to - is the instant a structure tears loose of the ground
 * and becomes a ship, or drops back into the world as blocks.
 *
 * Assembly reads as a release of pressure: the ground under the hull blows
 * outward in a ring, dust falls away from the underside as it separates, and
 * displaced air rolls off the silhouette. Disassembly is the opposite shape -
 * heavier, slower, settling rather than blasting.
 *
 * These are deliberately rare, one-shot set pieces. Nothing here repeats while
 * a ship is simply flying around, which is exactly what keeps them impressive.
 */
public final class AssemblyFx {

    private static final int MAX_EVENTS = 8;
    private static final Event[] EVENTS = new Event[MAX_EVENTS];

    static {
        for (int i = 0; i < MAX_EVENTS; i++) {
            EVENTS[i] = new Event();
        }
    }

    private static final class Event {
        boolean active;
        boolean assembling;
        double cx;
        double cy;
        double cz;
        double halfX;
        double halfZ;
        double baseY;
        int age;
        int duration;
    }

    /**
     * Queue a one-shot event.
     *
     * @param assembling true for lift-off, false for settling back down
     * @param bounds     the ship's world bounds at the moment it happened
     */
    public static void queue(boolean assembling, AABB bounds) {
        if (!VTConfig.ASSEMBLY_ENABLED.get()) {
            return;
        }
        Event slot = null;
        for (Event e : EVENTS) {
            if (!e.active) {
                slot = e;
                break;
            }
        }
        if (slot == null) {
            // All busy: replace whichever is closest to finishing.
            slot = EVENTS[0];
            for (Event e : EVENTS) {
                if (e.age > slot.age) {
                    slot = e;
                }
            }
        }
        Vec3 centre = bounds.getCenter();
        slot.active = true;
        slot.assembling = assembling;
        slot.cx = centre.x;
        slot.cy = centre.y;
        slot.cz = centre.z;
        slot.halfX = Math.max(0.75, (bounds.maxX - bounds.minX) * 0.5);
        slot.halfZ = Math.max(0.75, (bounds.maxZ - bounds.minZ) * 0.5);
        slot.baseY = bounds.minY;
        slot.age = 0;
        // Bigger ships take longer to break free and longer to settle.
        double size = Math.max(slot.halfX, slot.halfZ);
        int base = assembling ? 26 : 38;
        slot.duration = base + (int) Mth.clamp(size * 1.5, 0, 24);
    }

    public static boolean busy() {
        for (Event e : EVENTS) {
            if (e.active) {
                return true;
            }
        }
        return false;
    }

    public static void tick(ClientLevel level, RandomSource rng) {
        double scale = Budget.emissionScale() * VTConfig.ASSEMBLY_INTENSITY.get();
        for (Event e : EVENTS) {
            if (!e.active) {
                continue;
            }
            if (e.age++ >= e.duration) {
                e.active = false;
                continue;
            }
            if (scale > 0.0) {
                if (e.assembling) {
                    assembling(level, rng, e, scale);
                } else {
                    settling(level, rng, e, scale);
                }
            }
        }
    }

    // ---- lift-off ----

    private static void assembling(ClientLevel level, RandomSource rng, Event e, double scale) {
        double p = (double) e.age / e.duration;
        double radius = Math.max(e.halfX, e.halfZ);

        // The ring is a sharp outward pulse in the first third, then gone.
        if (p < 0.35) {
            double ringP = p / 0.35;
            double r = radius * (0.35 + 1.0 * ringP);
            double punch = 1.0 - ringP;
            int n = FxUtil.count(rng, (6.0 + radius * 1.2) * punch * scale);
            for (int i = 0; i < n; i++) {
                double angle = rng.nextDouble() * Mth.TWO_PI;
                double px = e.cx + Math.cos(angle) * r * (0.9 + rng.nextDouble() * 0.2);
                double pz = e.cz + Math.sin(angle) * r * (0.9 + rng.nextDouble() * 0.2);
                FxUtil.Probe probe = FxUtil.probeDown(level, px, e.baseY + 1.0, pz, 8.0);
                if (probe.type() == FxUtil.SurfaceType.NONE) {
                    continue;
                }
                double outX = Math.cos(angle) * (0.18 + 0.22 * punch);
                double outZ = Math.sin(angle) * (0.18 + 0.22 * punch);
                if (probe.type() == FxUtil.SurfaceType.WATER) {
                    FxUtil.vanilla(level, false, ParticleTypes.SPLASH,
                            px, probe.surfaceY() + 0.1, pz, outX, 0.25 * punch, outZ);
                    FxUtil.foam(level, false, px, probe.surfaceY() + 0.02, pz,
                            outX * 0.5, outZ * 0.5, 0.35f, 30 + rng.nextInt(20), 0.85f);
                } else {
                    FxUtil.vanilla(level, false,
                            new BlockParticleOption(ParticleTypes.BLOCK, probe.state()),
                            px, probe.surfaceY() + 0.2, pz, outX * 3.0, 0.35 * punch, outZ * 3.0);
                    FxUtil.wash(level, false, px, probe.surfaceY() + 0.4, pz,
                            outX, 0.05, outZ, 0.7f + rng.nextFloat() * 0.4f,
                            18 + rng.nextInt(12), 0.35f);
                }
            }
        }

        // Dust shaking loose from the underside as the hull separates.
        double fall = 1.0 - p;
        int n = FxUtil.count(rng, (3.0 + radius * 0.8) * fall * scale);
        for (int i = 0; i < n; i++) {
            double px = e.cx + (rng.nextDouble() - 0.5) * e.halfX * 2.0;
            double pz = e.cz + (rng.nextDouble() - 0.5) * e.halfZ * 2.0;
            FxUtil.smokeVapor(level, false, 0.62f + rng.nextFloat() * 0.15f,
                    px, e.baseY + rng.nextDouble() * 0.8, pz,
                    (rng.nextDouble() - 0.5) * 0.03,
                    -0.02 - rng.nextDouble() * 0.03,
                    (rng.nextDouble() - 0.5) * 0.03,
                    0.5f + rng.nextFloat() * 0.4f, 24 + rng.nextInt(18), 0.5f, 1.4f);
        }
    }

    // ---- settling back into the world ----

    private static void settling(ClientLevel level, RandomSource rng, Event e, double scale) {
        double p = (double) e.age / e.duration;
        double radius = Math.max(e.halfX, e.halfZ);
        // Heaviest right as it lands, then a long slow drift.
        double weight = p < 0.25 ? 1.0 : Math.max(0.0, 1.0 - (p - 0.25) / 0.75);

        int n = FxUtil.count(rng, (4.0 + radius * 1.0) * weight * scale);
        for (int i = 0; i < n; i++) {
            double px = e.cx + (rng.nextDouble() - 0.5) * e.halfX * 2.2;
            double pz = e.cz + (rng.nextDouble() - 0.5) * e.halfZ * 2.2;
            FxUtil.Probe probe = FxUtil.probeDown(level, px, e.baseY + 1.5, pz, 10.0);
            if (probe.type() == FxUtil.SurfaceType.NONE) {
                continue;
            }
            // Push outward from the centre, so the plume spreads rather than
            // sitting in a column.
            double dx = px - e.cx;
            double dz = pz - e.cz;
            double len = Math.sqrt(dx * dx + dz * dz);
            double outX = len < 1.0e-4 ? 0.0 : dx / len * 0.05 * weight;
            double outZ = len < 1.0e-4 ? 0.0 : dz / len * 0.05 * weight;

            if (probe.type() == FxUtil.SurfaceType.WATER) {
                FxUtil.vanilla(level, false, ParticleTypes.SPLASH,
                        px, probe.surfaceY() + 0.1, pz, outX * 2.0, 0.15 * weight, outZ * 2.0);
                FxUtil.foam(level, false, px, probe.surfaceY() + 0.02, pz, outX, outZ,
                        0.3f + rng.nextFloat() * 0.2f, 40 + rng.nextInt(25), 0.8f);
            } else {
                FxUtil.vanilla(level, false,
                        new BlockParticleOption(ParticleTypes.BLOCK, probe.state()),
                        px, probe.surfaceY() + 0.15, pz, outX * 2.0, 0.12 * weight, outZ * 2.0);
                FxUtil.smokeVapor(level, false, 0.68f + rng.nextFloat() * 0.15f,
                        px, probe.surfaceY() + 0.3 + rng.nextDouble() * 0.6, pz,
                        outX, 0.02 + rng.nextDouble() * 0.02, outZ,
                        0.6f + rng.nextFloat() * 0.5f, 35 + rng.nextInt(25), 0.45f, 1.6f);
            }
        }
    }

    public static void reset() {
        for (Event e : EVENTS) {
            e.active = false;
            e.age = 0;
        }
    }

    private AssemblyFx() {}
}
