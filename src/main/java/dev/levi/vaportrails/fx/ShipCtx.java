package dev.levi.vaportrails.fx;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Per-tick, world-space snapshot of one Sable ship. Everything is expressed
 * in vanilla types so the effect classes never touch Sable/Create classes.
 *
 * @param velocity ship velocity in blocks per tick
 * @param speedMs  |velocity| in metres (blocks) per second
 */
public record ShipCtx(UUID id, Vec3 center, Vec3 velocity, double speedMs, AABB bounds,
                      List<PropCtx> props, List<EngineCtx> engines, ShipFxState st) {

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
     * @param rpm     signed Create rotation speed
     * @param thrust  signed thrust along the axis (0 when unknown)
     * @param washDir world-space air stream direction if the mod could read it
     *                directly (bearing props expose it); null means "infer it"
     */
    public record PropCtx(Vec3 pos, Vec3 axis, double radius, double rpm, double thrust,
                          boolean active, Vec3 washDir, PropFxState st) {}

    public record EngineCtx(Vec3 pos, boolean running, EngineFxState st) {}
}
