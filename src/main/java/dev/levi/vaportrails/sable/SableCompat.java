package dev.levi.vaportrails.sable;

import dev.levi.vaportrails.VaporTrails;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/**
 * The single gateway between Vapor Trails and Sable. This class deliberately
 * references no Sable/Create/Aeronautics types, so it always classloads; the
 * actual work lives in {@link SableTicker}, which is only touched when the
 * Sable mod is present. If Sable is absent (or its internals ever change and
 * start throwing), the mod silently does nothing instead of crashing.
 */
public final class SableCompat {

    private static Boolean sableLoaded;
    private static int errorStrikes;
    private static boolean disabled;

    public static boolean available() {
        if (disabled) {
            return false;
        }
        if (sableLoaded == null) {
            sableLoaded = ModList.get().isLoaded("sable");
            if (sableLoaded) {
                VaporTrails.LOGGER.info("Sable detected - vapor trail effects enabled.");
            } else {
                VaporTrails.LOGGER.info("Sable not installed - vapor trail effects idle.");
            }
        }
        return sableLoaded;
    }

    public static void tick(Minecraft mc) {
        try {
            SableTicker.tick(mc);
        } catch (Throwable t) {
            if (++errorStrikes >= 5) {
                disabled = true;
                VaporTrails.LOGGER.error(
                        "Repeated errors talking to Sable - disabling all vapor trail effects for this session.", t);
            } else {
                VaporTrails.LOGGER.warn("Error during vapor trail tick (strike {}/5)", errorStrikes, t);
            }
        }
    }

    public static void reset() {
        if (sableLoaded != null && sableLoaded && !disabled) {
            try {
                SableTicker.reset();
            } catch (Throwable ignored) {
                // Nothing sane to do during logout.
            }
        }
        errorStrikes = 0;
    }

    private SableCompat() {}
}
