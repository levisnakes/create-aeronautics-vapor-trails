package dev.levi.vaportrails.sable;

import dev.levi.vaportrails.VTConfig;
import dev.levi.vaportrails.fx.Budget;
import dev.levi.vaportrails.fx.CloudPunchFx;
import dev.levi.vaportrails.fx.EngineFx;
import dev.levi.vaportrails.fx.HoverDustFx;
import dev.levi.vaportrails.fx.PropellerFx;
import dev.levi.vaportrails.fx.ShipCtx;
import dev.levi.vaportrails.fx.WaterWakeFx;
import dev.levi.vaportrails.fx.WingtipFx;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sable-typed core loop: enumerate the client's loaded sub-levels each tick,
 * maintain per-ship state, and drive the effect emitters. Classloaded only
 * when Sable is installed (see {@link SableCompat}).
 */
final class SableTicker {

    private static final Map<UUID, ShipState> STATES = new HashMap<>();
    private static final Set<UUID> SEEN = new HashSet<>();
    private static ClientLevel lastLevel;
    private static int tickCount;

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            STATES.clear();
            lastLevel = level;
        }
        tickCount++;

        ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            STATES.clear();
            return;
        }
        List<ClientSubLevel> subs = container.getAllSubLevels();
        if (subs.isEmpty()) {
            // No physics objects loaded: skip all work.
            if (!STATES.isEmpty()) {
                STATES.clear();
            }
            return;
        }

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double range = VTConfig.EFFECT_RANGE.get();
        double rangeSq = range * range;
        RandomSource rng = level.random;

        SEEN.clear();
        for (ClientSubLevel sub : subs) {
            if (sub == null || sub.isRemoved()) {
                continue;
            }
            UUID id = sub.getUniqueId();
            if (id == null) {
                continue;
            }
            SEEN.add(id);
            ShipState state = STATES.computeIfAbsent(id, ShipState::new);
            state.updateMotion(sub);

            if (sub.boundingBox() == null) {
                continue;
            }
            org.joml.Vector3d c = sub.boundingBox().center();
            double dx = c.x - cam.x;
            double dy = c.y - cam.y;
            double dz = c.z - cam.z;
            if (dx * dx + dy * dy + dz * dz > rangeSq) {
                continue; // beyond effect range: track motion only
            }

            state.maybeRescan(sub);
            ShipCtx ctx = state.buildCtx(sub);
            if (ctx == null) {
                continue;
            }

            Budget.beginShip();
            if (VTConfig.DEBUG_MARKERS.get() && tickCount % 5 == 0) {
                debugMarkers(level, ctx);
            }
            // Propellers first: they draw from their own sub-budget and must
            // not be starved by the ambient effects.
            PropellerFx.tick(level, ctx, rng);
            WingtipFx.tick(level, ctx, rng);
            EngineFx.tick(level, ctx, rng);
            HoverDustFx.tick(level, ctx, rng);
            WaterWakeFx.tick(level, ctx, rng);
            CloudPunchFx.tick(level, ctx, rng);
        }
        STATES.keySet().retainAll(SEEN);
    }

    /** Tracking/detection proof: end-rod motes at ship centre, prop hubs and blade tips. */
    private static void debugMarkers(ClientLevel level, ShipCtx ctx) {
        level.addParticle(ParticleTypes.END_ROD,
                ctx.center().x, ctx.center().y, ctx.center().z,
                ctx.velocity().x, ctx.velocity().y, ctx.velocity().z);
        for (ShipCtx.PropCtx prop : ctx.props()) {
            level.addParticle(ParticleTypes.END_ROD,
                    prop.pos().x, prop.pos().y, prop.pos().z, 0, 0, 0);
            Vec3 tip = prop.pos().add(
                    dev.levi.vaportrails.fx.FxUtil.perpendicular(prop.axis()).scale(prop.radius()));
            level.addParticle(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 0, 0, 0);
        }
        for (ShipCtx.EngineCtx engine : ctx.engines()) {
            level.addParticle(ParticleTypes.FLAME,
                    engine.pos().x, engine.pos().y + 1.0, engine.pos().z, 0, 0, 0);
        }
    }

    static void reset() {
        STATES.clear();
        lastLevel = null;
    }

    private SableTicker() {}
}
