package dev.levi.vaportrails.sable;

import dev.levi.vaportrails.VTConfig;
import dev.levi.vaportrails.VaporTrails;
import dev.levi.vaportrails.fx.AssemblyFx;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Watches Sable for ships appearing and disappearing, and turns the ones that
 * are genuinely assemblies or disassemblies into {@link AssemblyFx} events.
 *
 * The hard part is telling a real assembly from a ship that merely streamed
 * into view. Sable's finalize packet fires for both, so this uses two signals
 * together:
 *
 *  - a ship that unloads and reloads keeps its UUID, while a structure that is
 *    assembled afresh gets a brand new one, so an id seen earlier this session
 *    is a reload rather than an assembly;
 *  - an assembly is triggered by somebody interacting with a block, so it
 *    happens near a player - anything appearing far away is streaming in, and
 *    would be invisible anyway.
 *
 * Disassembly needs no guesswork: Sable reports {@code REMOVED} for a ship that
 * was actually taken apart and {@code UNLOADED} for one that just left range.
 *
 * Callbacks here run inside Sable's own code, so everything is wrapped: a stray
 * exception from a cosmetic mod must never take the game down with it.
 */
final class SubLevelWatcher implements SubLevelObserver {

    /** Ids seen this session, so reloads are not mistaken for assemblies. */
    private static final int MAX_REMEMBERED = 512;
    private final Set<UUID> seen = new LinkedHashSet<>();

    @Override
    public void onSubLevelAdded(SubLevel subLevel) {
        try {
            UUID id = subLevel.getUniqueId();
            if (id == null) {
                return;
            }
            boolean firstTime = remember(id);
            BoundingBox3dc bb = subLevel.boundingBox();
            if (bb == null) {
                return;
            }
            if (!firstTime || !nearCamera(bb.center().x, bb.center().y, bb.center().z)) {
                return;
            }
            AssemblyFx.queue(true, bb.toMojang());
        } catch (Throwable t) {
            VaporTrails.LOGGER.warn("Vapor Trails failed to handle a ship appearing", t);
        }
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        try {
            // UNLOADED just means it left our view; only REMOVED is a real
            // disassembly. Firing a plume every time a ship drifts out of
            // range would be worse than having no effect at all.
            if (reason != SubLevelRemovalReason.REMOVED) {
                return;
            }
            BoundingBox3dc bb = subLevel.boundingBox();
            if (bb == null) {
                return;
            }
            if (!nearCamera(bb.center().x, bb.center().y, bb.center().z)) {
                return;
            }
            AssemblyFx.queue(false, bb.toMojang());
        } catch (Throwable t) {
            VaporTrails.LOGGER.warn("Vapor Trails failed to handle a ship being removed", t);
        }
    }

    /** Record an id; true when it had not been seen before. */
    private boolean remember(UUID id) {
        boolean fresh = seen.add(id);
        if (seen.size() > MAX_REMEMBERED) {
            // Oldest first, courtesy of LinkedHashSet.
            var it = seen.iterator();
            it.next();
            it.remove();
        }
        return fresh;
    }

    private static boolean nearCamera(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double range = VTConfig.ASSEMBLY_RANGE.get();
        double dx = x - cam.x;
        double dy = y - cam.y;
        double dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    void forget() {
        seen.clear();
    }
}
