package dev.levi.vaportrails.particle;

import dev.levi.vaportrails.VaporTrails;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Particle type registration + client provider wiring. */
public final class VTParticles {

    public static final DeferredRegister<ParticleType<?>> TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, VaporTrails.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VAPOR =
            TYPES.register("vapor", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WASH =
            TYPES.register("wash", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FOAM =
            TYPES.register("foam", () -> new SimpleParticleType(false));

    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(VAPOR.get(), VaporParticle.Provider::new);
        event.registerSpriteSet(WASH.get(), WashParticle.Provider::new);
        event.registerSpriteSet(FOAM.get(), FoamParticle.Provider::new);
    }

    private VTParticles() {}
}
