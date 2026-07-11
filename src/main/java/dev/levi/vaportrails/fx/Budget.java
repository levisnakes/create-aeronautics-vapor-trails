package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.VTConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;

/**
 * Particle budgeting. Tracks live custom particles globally, plus per-ship
 * and per-ship-propeller spawn caps each tick so one busy ship (or one ship
 * with a dozen propellers) can't starve everything else.
 *
 * All access is client-thread only; plain ints are fine.
 */
public final class Budget {

    private static int live;

    // Cached once per tick.
    private static int maxLive = 800;
    private static double statusMult = 1.0;
    private static double master = 1.0;

    // Per-ship counters.
    private static int shipSpawned;
    private static int shipCap;
    private static int propSpawned;
    private static int propCap;

    public static void onSpawn() {
        live++;
    }

    public static void onRemove() {
        live--;
    }

    /** Refresh config/graphics-derived values; call once per client tick. */
    public static void tickStart(Minecraft mc) {
        maxLive = VTConfig.MAX_PARTICLES.get();
        master = VTConfig.MASTER_INTENSITY.get();
        ParticleStatus status = mc.options.particles().get();
        statusMult = switch (status) {
            case ALL -> 1.0;
            case DECREASED -> 0.55;
            case MINIMAL -> 0.2;
        };
    }

    /** Reset per-ship counters; call before emitting a ship's effects. */
    public static void beginShip() {
        shipSpawned = 0;
        shipCap = VTConfig.PER_SHIP_SPAWNS_PER_TICK.get();
        propCap = (int) Math.ceil(shipCap * VTConfig.PROPELLER_BUDGET_SHARE.get());
        propSpawned = 0;
    }

    /**
     * Reserve one particle slot. {@code propeller} spawns draw from the
     * propeller sub-budget; {@code countsLive} is true for our custom
     * particles which are tracked against the global live cap.
     */
    public static boolean tryTake(boolean propeller, boolean countsLive) {
        if (countsLive && live >= maxLive) {
            return false;
        }
        if (shipSpawned >= shipCap) {
            return false;
        }
        if (propeller && propSpawned >= propCap) {
            return false;
        }
        shipSpawned++;
        if (propeller) {
            propSpawned++;
        }
        return true;
    }

    /** Master intensity x particle-setting degradation, applied to emission rates. */
    public static double emissionScale() {
        return master * statusMult;
    }

    /** True when the vanilla particle setting is MINIMAL - ambient-only effects should skip. */
    public static boolean minimalParticles() {
        return statusMult <= 0.2;
    }

    public static void reset() {
        live = 0;
    }

    private Budget() {}
}
