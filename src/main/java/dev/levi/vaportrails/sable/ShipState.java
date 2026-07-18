package dev.levi.vaportrails.sable;

import dev.levi.vaportrails.fx.EngineFxState;
import dev.levi.vaportrails.fx.PropFxState;
import dev.levi.vaportrails.fx.ShipCtx;
import dev.levi.vaportrails.fx.ShipFxState;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent per-ship tracking state: velocity derived by diffing the pose
 * between client ticks, plus a periodically-refreshed cache of the propeller
 * and engine block entities found in the ship's plot.
 */
final class ShipState {

    /** Ticks between plot block-entity rescans. */
    private static final int RESCAN_INTERVAL = 40;
    /** Pose jumps beyond this (blocks/tick) are teleports, not motion. */
    private static final double TELEPORT_SPEED_SQ = 100.0;

    final UUID id;

    private final Vector3d lastPos = new Vector3d();
    private boolean hasLast;
    private Vec3 velocity = Vec3.ZERO;

    private final ShipFxState fxState = new ShipFxState();
    private final Map<BlockPos, PropFxState> propFx = new HashMap<>();
    private final Map<BlockPos, EngineFxState> engineFx = new HashMap<>();
    private final List<BlockEntity> propBes = new ArrayList<>();
    private final List<BlockEntity> engineBes = new ArrayList<>();
    private int rescanIn;

    ShipState(UUID id) {
        this.id = id;
    }

    /** Update velocity every tick, even while culled, so re-entering range never spikes. */
    void updateMotion(ClientSubLevel sub) {
        Vector3dc pos = sub.logicalPose().position();
        if (hasLast) {
            double vx = pos.x() - lastPos.x;
            double vy = pos.y() - lastPos.y;
            double vz = pos.z() - lastPos.z;
            double sq = vx * vx + vy * vy + vz * vz;
            velocity = sq > TELEPORT_SPEED_SQ ? Vec3.ZERO : new Vec3(vx, vy, vz);
        }
        lastPos.set(pos);
        hasLast = true;
    }

    void maybeRescan(ClientSubLevel sub) {
        if (--rescanIn > 0) {
            return;
        }
        rescanIn = RESCAN_INTERVAL + (id.hashCode() & 15); // stagger ships
        propBes.clear();
        engineBes.clear();
        LevelPlot plot = sub.getPlot();
        if (plot == null) {
            return;
        }
        for (PlotChunkHolder holder : plot.getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null) {
                continue;
            }
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be == null || be.isRemoved()) {
                    continue;
                }
                if (be instanceof BlockEntityPropeller) {
                    propBes.add(be);
                    propFx.computeIfAbsent(be.getBlockPos(), p -> new PropFxState());
                } else if (Mods.CREATE && CreateCompat.isEngine(be)) {
                    engineBes.add(be);
                    engineFx.computeIfAbsent(be.getBlockPos(), p -> new EngineFxState());
                }
            }
        }
    }

    /** World-space snapshot for the effect classes; null if the ship has no usable bounds yet. */
    ShipCtx buildCtx(ClientSubLevel sub) {
        BoundingBox3dc bb = sub.boundingBox();
        if (bb == null) {
            return null;
        }
        Pose3dc pose = sub.logicalPose();
        AABB bounds = bb.toMojang();

        List<ShipCtx.PropCtx> props = propBes.isEmpty() ? List.of() : new ArrayList<>(propBes.size());
        for (BlockEntity be : propBes) {
            if (be.isRemoved() || !(be instanceof BlockEntityPropeller prop)) {
                continue;
            }
            Direction facing = prop.getBlockDirection();
            if (facing == null) {
                continue;
            }
            BlockPos plotPos = be.getBlockPos();
            double offset = Mods.AERONAUTICS ? AeroCompat.discOffset(be) : 0.45;
            Vec3 discPlot = new Vec3(
                    plotPos.getX() + 0.5 + facing.getStepX() * offset,
                    plotPos.getY() + 0.5 + facing.getStepY() * offset,
                    plotPos.getZ() + 0.5 + facing.getStepZ() * offset);
            Vec3 worldPos = pose.transformPosition(discPlot);
            Vec3 axis = pose.transformNormal(new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()))
                    .normalize();
            double rpm;
            boolean active = prop.isActive();
            Vec3 washDir = null;
            if (Mods.AERONAUTICS && AeroCompat.isBearing(be)) {
                // Bearing (contraption) props: the client BE's kinetic speed is
                // unreliable, so read the contraption's angular speed, and take
                // the wash direction straight from its thrust vector.
                rpm = AeroCompat.bearingRpm(be);
                active = active || AeroCompat.bearingSpinning(be);
                Vec3 thrustLocal = AeroCompat.bearingThrustDir(be);
                if (thrustLocal != null) {
                    washDir = pose.transformNormal(thrustLocal).normalize().scale(-1.0);
                }
            } else {
                rpm = Mods.CREATE ? CreateCompat.rpm(be) : (prop.isActive() ? 96.0 : 0.0);
            }
            double radius = Mods.AERONAUTICS ? AeroCompat.radius(be) : 1.5;
            props.add(new ShipCtx.PropCtx(worldPos, axis, radius, rpm, prop.getThrust(),
                    active, washDir, propFx.get(plotPos)));
        }

        List<ShipCtx.EngineCtx> engines = engineBes.isEmpty() ? List.of() : new ArrayList<>(engineBes.size());
        for (BlockEntity be : engineBes) {
            if (be.isRemoved()) {
                continue;
            }
            BlockPos plotPos = be.getBlockPos();
            Vec3 worldPos = pose.transformPosition(
                    new Vec3(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5));
            engines.add(new ShipCtx.EngineCtx(worldPos, CreateCompat.isRunning(be), engineFx.get(plotPos)));
        }

        return new ShipCtx(id, bounds.getCenter(), velocity, velocity.length() * 20.0,
                bounds, props, engines, fxState);
    }
}
