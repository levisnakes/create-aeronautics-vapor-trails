package dev.levi.vaportrails.sable;

import net.neoforged.fml.ModList;

/**
 * Presence flags for optional dependencies. These live here (referencing no
 * modded types) so guard checks never classload the compat classes that do.
 */
public final class Mods {
    public static final boolean CREATE = ModList.get().isLoaded("create");
    public static final boolean AERONAUTICS = ModList.get().isLoaded("aeronautics");

    private Mods() {}
}
