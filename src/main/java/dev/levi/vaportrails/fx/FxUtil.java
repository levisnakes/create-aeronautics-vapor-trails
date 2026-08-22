package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.particle.FoamParticle;
import dev.levi.vaportrails.particle.VTParticles;
import dev.levi.vaportrails.particle.VaporParticle;
import dev.levi.vaportrails.particle.WashParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/** Spawn helpers and small geometry utilities shared by the effects. */
public final class FxUtil {

    // ---- spawning ----

    public static void vapor(ClientLevel level, boolean propBudget,
                             double x, double y, double z, double vx, double vy, double vz,
                             float scale, int lifetime, float alpha, float growth) {
        if (!Budget.tryTake(propBudget, true)) {
            return;
        }
        Particle p = Minecraft.getInstance().particleEngine
                .createParticle(VTParticles.VAPOR.get(), x, y, z, vx, vy, vz);
        if (p instanceof VaporParticle vp) {
            vp.setup(scale, lifetime, alpha, growth);
        }
    }

    public static void smokeVapor(ClientLevel level, boolean propBudget, float shade,
                                  double x, double y, double z, double vx, double vy, double vz,
                                  float scale, int lifetime, float alpha, float growth) {
        if (!Budget.tryTake(propBudget, true)) {
            return;
        }
        Particle p = Minecraft.getInstance().particleEngine
                .createParticle(VTParticles.VAPOR.get(), x, y, z, vx, vy, vz);
        if (p instanceof VaporParticle vp) {
            vp.setup(scale, lifetime, alpha, growth).setGray(shade);
        }
    }

    public static void wash(ClientLevel level, boolean propBudget,
                            double x, double y, double z, double vx, double vy, double vz,
                            float scale, int lifetime, float alpha) {
        if (!Budget.tryTake(propBudget, true)) {
            return;
        }
        Particle p = Minecraft.getInstance().particleEngine
                .createParticle(VTParticles.WASH.get(), x, y, z, vx, vy, vz);
        if (p instanceof WashParticle wp) {
            wp.setup(scale, lifetime, alpha);
        }
    }

    public static void foam(ClientLevel level, boolean propBudget,
                            double x, double y, double z, double vx, double vz,
                            float scale, int lifetime, float alpha) {
        if (!Budget.tryTake(propBudget, true)) {
            return;
        }
        Particle p = Minecraft.getInstance().particleEngine
                .createParticle(VTParticles.FOAM.get(), x, y, z, vx, 0.0, vz);
        if (p instanceof FoamParticle fp) {
            fp.setup(scale, lifetime, alpha);
        }
    }

    /** Vanilla particle routed through the same per-ship budget (not the live cap). */
    public static void vanilla(ClientLevel level, boolean propBudget, ParticleOptions options,
                               double x, double y, double z, double vx, double vy, double vz) {
        if (!Budget.tryTake(propBudget, false)) {
            return;
        }
        level.addParticle(options, x, y, z, vx, vy, vz);
    }

    /** Turn a fractional per-tick rate into an integer count (probabilistic rounding). */
    public static int count(RandomSource rng, double rate) {
        int whole = (int) rate;
        if (rng.nextDouble() < rate - whole) {
            whole++;
        }
        return whole;
    }

    // ---- geometry ----

    /** Any unit vector perpendicular to {@code axis}. */
    public static Vec3 perpendicular(Vec3 axis) {
        Vec3 ref = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return axis.cross(ref).normalize();
    }

    // ---- ground probing ----

    public enum SurfaceType { NONE, GROUND, WATER }

    public record Probe(SurfaceType type, double surfaceY, BlockState state) {
        public static final Probe NONE = new Probe(SurfaceType.NONE, 0, null);
    }

    /** Where a propeller's slipstream lands. */
    public record Impact(SurfaceType type, Vec3 pos, BlockState state) {
        public static final Impact NONE = new Impact(SurfaceType.NONE, Vec3.ZERO, null);
    }

    /**
     * March along {@code dir} from {@code origin} and report the first water or
     * solid surface within {@code maxDist}. Used to find where a propeller's
     * downwash actually strikes, which is only straight down for a lift prop.
     */
    public static Impact probeRay(ClientLevel level, Vec3 origin, Vec3 dir, double maxDist) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double step = 0.5;
        int steps = (int) Math.ceil(maxDist / step);
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int i = 1; i <= steps; i++) {
            double d = i * step;
            double px = origin.x + dir.x * d;
            double py = origin.y + dir.y * d;
            double pz = origin.z + dir.z * d;
            int xi = Mth.floor(px);
            int yi = Mth.floor(py);
            int zi = Mth.floor(pz);
            if (xi == lastX && yi == lastY && zi == lastZ) {
                continue; // same block as the previous step
            }
            lastX = xi;
            lastY = yi;
            lastZ = zi;
            pos.set(xi, yi, zi);
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.isEmpty()) {
                double surface = yi + fluid.getHeight(level, pos);
                return new Impact(SurfaceType.WATER, new Vec3(px, surface, pz), level.getBlockState(pos));
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                double top = yi + state.getCollisionShape(level, pos)
                        .max(net.minecraft.core.Direction.Axis.Y);
                return new Impact(SurfaceType.GROUND, new Vec3(px, top, pz), state);
            }
        }
        return Impact.NONE;
    }

    /**
     * Scan straight down from (x, yStart, z) for up to {@code maxDepth} blocks
     * and report the first water or solid surface.
     */
    public static Probe probeDown(ClientLevel level, double x, double yStart, double z, double maxDepth) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int xi = Mth.floor(x);
        int zi = Mth.floor(z);
        int top = Mth.floor(yStart);
        int bottom = Math.max(level.getMinBuildHeight(), Mth.floor(yStart - maxDepth));
        for (int y = top; y >= bottom; y--) {
            pos.set(xi, y, zi);
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.isEmpty()) {
                return new Probe(SurfaceType.WATER, y + fluid.getHeight(level, pos), level.getBlockState(pos));
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                double h = state.getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y);
                return new Probe(SurfaceType.GROUND, y + h, state);
            }
        }
        return Probe.NONE;
    }

    private FxUtil() {}
}
