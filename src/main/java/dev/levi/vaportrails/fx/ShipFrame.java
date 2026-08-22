package dev.levi.vaportrails.fx;

import net.minecraft.world.phys.Vec3;

/**
 * A ship's local-to-world transform, captured once per tick as an affine frame
 * so the effect classes can move between ship space and world space without
 * touching Sable's pose types.
 *
 * {@code origin} is where local (0,0,0) lands, and the three axis vectors are
 * where the local unit axes point in world space.
 */
public record ShipFrame(Vec3 origin, Vec3 axisX, Vec3 axisY, Vec3 axisZ) {

    /** Ship-local point to world space. */
    public Vec3 toWorld(double lx, double ly, double lz) {
        return new Vec3(
                origin.x + axisX.x * lx + axisY.x * ly + axisZ.x * lz,
                origin.y + axisX.y * lx + axisY.y * ly + axisZ.y * lz,
                origin.z + axisX.z * lx + axisY.z * ly + axisZ.z * lz);
    }

    /** World direction to ship space. Axes are unit length, so this is a projection. */
    public Vec3 dirToLocal(Vec3 world) {
        return new Vec3(world.dot(axisX), world.dot(axisY), world.dot(axisZ));
    }

    /** Ship-space direction back to world space. */
    public Vec3 dirToWorld(double lx, double ly, double lz) {
        return new Vec3(
                axisX.x * lx + axisY.x * ly + axisZ.x * lz,
                axisX.y * lx + axisY.y * ly + axisZ.y * lz,
                axisX.z * lx + axisY.z * ly + axisZ.z * lz);
    }
}
