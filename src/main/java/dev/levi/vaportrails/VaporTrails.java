package dev.levi.vaportrails;

import dev.levi.vaportrails.particle.VTParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create Aeronautics: Vapor Trails - 100% client-side atmospheric particle
 * effects for Sable physics objects (airships, planes, cars).
 *
 * The mod never touches server state and sends no packets; it only reads
 * Sable's client-side sub-level tracking and spawns vanilla-engine particles.
 */
@Mod(VaporTrails.MOD_ID)
public final class VaporTrails {

    public static final String MOD_ID = "vaportrails";
    public static final Logger LOGGER = LoggerFactory.getLogger("VaporTrails");

    public VaporTrails(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, VTConfig.SPEC);

        // Particle *types* are side-neutral; registering them on both dists keeps
        // registries consistent even if someone wrongly installs this on a server.
        VTParticles.TYPES.register(modBus);

        // Everything else is client-only. On a dedicated server (which should
        // never happen given side="CLIENT" deps) we register nothing more.
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        modBus.addListener(VTParticles::registerProviders);

        // Free in-game config screen provided by NeoForge - no extra dependency.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        ClientEvents.register();
    }
}
