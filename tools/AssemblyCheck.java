/**
 * Validates the rules SubLevelWatcher uses to decide whether a ship appearing
 * or disappearing is a real assembly / disassembly, or just streaming in and
 * out of view.
 *
 * Getting this wrong is worse than having no effect at all: a false positive
 * fires a dust explosion every time a ship drifts into render distance. The
 * decision logic is reproduced here so it can be exercised directly.
 */
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class AssemblyCheck {

    enum Reason { UNLOADED, REMOVED }

    static final int MAX_REMEMBERED = 512;
    static final double RANGE = 64.0;

    static final Set<UUID> seen = new LinkedHashSet<>();

    /** Verbatim from SubLevelWatcher.remember. */
    static boolean remember(UUID id) {
        boolean fresh = seen.add(id);
        if (seen.size() > MAX_REMEMBERED) {
            var it = seen.iterator();
            it.next();
            it.remove();
        }
        return fresh;
    }

    static boolean nearCamera(double dist) {
        return dist * dist <= RANGE * RANGE;
    }

    /** Would an "added" callback fire an assembly burst? */
    static boolean onAdded(UUID id, double distance) {
        boolean firstTime = remember(id);
        return firstTime && nearCamera(distance);
    }

    /** Would a "removed" callback fire a settling plume? */
    static boolean onRemoved(Reason reason, double distance) {
        if (reason != Reason.REMOVED) return false;
        return nearCamera(distance);
    }

    static int failures = 0;

    static void check(String label, boolean actual, boolean expected) {
        boolean ok = actual == expected;
        System.out.printf("  %-56s %s%n", label, ok ? "ok" : "FAIL (expected " + expected + ")");
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        System.out.println("Assembly detection");
        UUID fresh = UUID.randomUUID();
        check("brand new ship, right next to you -> assembly", onAdded(fresh, 10), true);
        check("same ship reappearing after unload -> no burst", onAdded(fresh, 10), false);
        check("same ship again, still no burst", onAdded(fresh, 5), false);

        UUID distant = UUID.randomUUID();
        check("unknown ship streaming in at 120 blocks -> no burst", onAdded(distant, 120), false);
        // Note the deliberate consequence: it is now remembered, so when you
        // fly closer and it reloads, it still will not falsely fire.
        check("...and once remembered, closing to 10 blocks stays quiet",
                onAdded(distant, 10), false);

        UUID edge = UUID.randomUUID();
        check("exactly at the range limit counts", onAdded(edge, 64.0), true);
        UUID justOut = UUID.randomUUID();
        check("just beyond the range limit does not", onAdded(justOut, 64.1), false);

        // Re-assembling a structure produces a NEW sub-level id, so it should
        // fire again even though "the same ship" was seen before.
        UUID reassembled = UUID.randomUUID();
        check("re-assembled ship (new id) fires again", onAdded(reassembled, 12), true);

        System.out.println("Disassembly detection");
        check("REMOVED nearby -> settling plume", onRemoved(Reason.REMOVED, 20), true);
        check("UNLOADED nearby -> nothing (just left view)", onRemoved(Reason.UNLOADED, 20), false);
        check("UNLOADED far -> nothing", onRemoved(Reason.UNLOADED, 300), false);
        check("REMOVED far away -> nothing (cannot be seen)", onRemoved(Reason.REMOVED, 300), false);

        System.out.println("Memory bound");
        seen.clear();
        UUID first = UUID.randomUUID();
        remember(first);
        for (int i = 0; i < MAX_REMEMBERED - 1; i++) {
            remember(UUID.randomUUID());
        }
        check("set is exactly at capacity", seen.size() == MAX_REMEMBERED, true);
        check("earliest id still remembered at capacity", seen.contains(first), true);
        // One more push evicts the oldest.
        remember(UUID.randomUUID());
        check("stays capped after overflow", seen.size() == MAX_REMEMBERED, true);
        check("oldest id evicted first (FIFO)", seen.contains(first), false);

        // Sanity: a heavy session must not grow without bound.
        for (int i = 0; i < 5000; i++) {
            remember(UUID.randomUUID());
        }
        check("still capped after 5000 ships", seen.size() == MAX_REMEMBERED, true);

        System.out.println();
        System.out.println(failures == 0 ? "all checks passed" : failures + " CHECK(S) FAILED");
        if (failures > 0) System.exit(1);
    }
}
