package dev.levi.vaportrails.sable;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.eriksonn.aeronautics.content.blocks.propeller.bearing.propeller_bearing.PropellerBearingBlockEntity;
import dev.eriksonn.aeronautics.content.blocks.propeller.behaviour.PropellerActorBehaviour;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Create Aeronautics-typed reads (blade radius, disc offset). Only call when
 * {@link Mods#AERONAUTICS} is true - this class must never be classloaded
 * without Create Aeronautics present.
 */
final class AeroCompat {

    /** Blade-tip radius in blocks. */
    static double radius(BlockEntity be) {
        if (be instanceof BasePropellerBlockEntity small) {
            float r = small.getRadius();
            if (r > 0.1f && r < 32.0f) {
                return r;
            }
        }
        if (be instanceof PropellerBearingBlockEntity bearing) {
            PropellerActorBehaviour behaviour = BlockEntityBehaviour.get(bearing, PropellerActorBehaviour.TYPE);
            if (behaviour != null && behaviour.radius > 0.1 && behaviour.radius < 64.0) {
                return behaviour.radius;
            }
            return 4.0; // bearing prop with unknown sails: assume a big disc
        }
        return 1.5;
    }

    static boolean isBearing(BlockEntity be) {
        return be instanceof PropellerBearingBlockEntity;
    }

    /**
     * Effective rotation speed for a bearing prop, in Create-RPM units. The
     * client-side bearing BE can report 0 kinetic speed while its contraption
     * visibly spins, so prefer the contraption's angular speed (deg/tick;
     * Create visuals map speed -> speed * 0.3 deg/tick, hence / 0.3).
     */
    static double bearingRpm(BlockEntity be) {
        if (!(be instanceof PropellerBearingBlockEntity bearing)) {
            return 0.0;
        }
        float angular = bearing.getAngularSpeed();
        if (Math.abs(angular) > 1.0e-3f) {
            return angular / 0.3;
        }
        return bearing.getSpeed();
    }

    /** True when the bearing has an assembled, spinning blade contraption. */
    static boolean bearingSpinning(BlockEntity be) {
        return be instanceof PropellerBearingBlockEntity bearing
                && bearing.getMovedContraption() != null
                && Math.abs(bearing.getAngularSpeed()) > 1.0e-3f;
    }

    /**
     * The bearing's own thrust direction (plot-local), or null. Air streams
     * the opposite way, so this beats guessing from RPM signs.
     */
    static net.minecraft.world.phys.Vec3 bearingThrustDir(BlockEntity be) {
        if (!(be instanceof PropellerBearingBlockEntity bearing)) {
            return null;
        }
        org.joml.Vector3d t = bearing.thrustDirection;
        if (t == null || t.lengthSquared() < 1.0e-6) {
            return null;
        }
        return new net.minecraft.world.phys.Vec3(t.x, t.y, t.z).normalize();
    }

    /** Distance from the hub block centre to the blade disc, along the facing. */
    static double discOffset(BlockEntity be) {
        if (be instanceof PropellerBearingBlockEntity) {
            return 1.0; // the contraption spins one block out from the bearing
        }
        if (be instanceof BasePropellerBlockEntity small) {
            float o = small.getOffset();
            if (o > -2.0f && o < 2.0f) {
                return o;
            }
        }
        return 0.45;
    }

    private AeroCompat() {}
}
