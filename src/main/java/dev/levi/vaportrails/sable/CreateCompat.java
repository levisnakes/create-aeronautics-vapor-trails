package dev.levi.vaportrails.sable;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Locale;

/**
 * Create-typed reads. Only call when {@link Mods#CREATE} is true - this class
 * must never be classloaded without Create present.
 */
final class CreateCompat {

    /** Signed Create rotation speed (RPM), 0 for non-kinetic blocks. */
    static double rpm(BlockEntity be) {
        return be instanceof KineticBlockEntity kinetic ? kinetic.getSpeed() : 0.0;
    }

    /**
     * Heuristic engine detection: any speed-generating kinetic block whose
     * class calls itself an engine. Covers Create Aeronautics' portable
     * engine ("simulated" module) plus addon engines (e.g. diesel
     * generators), while excluding water wheels, windmill bearings etc.
     */
    static boolean isEngine(BlockEntity be) {
        return be instanceof GeneratingKineticBlockEntity
                && be.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("engine");
    }

    static boolean isRunning(BlockEntity be) {
        return be instanceof GeneratingKineticBlockEntity generating
                && Math.abs(generating.getGeneratedSpeed()) > 0.01f;
    }

    private CreateCompat() {}
}
