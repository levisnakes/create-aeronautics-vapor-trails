package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.VTConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/**
 * Effect 3: engine smoke. Light grey puffs while an engine runs; briefly
 * darker and denser right after startup.
 */
public final class EngineFx {

    public static void tick(ClientLevel level, ShipCtx ship, RandomSource rng) {
        if (!VTConfig.ENGINE_ENABLED.get()) {
            return;
        }
        double scale = Budget.emissionScale() * VTConfig.ENGINE_INTENSITY.get();
        if (scale <= 0.0) {
            return;
        }
        for (ShipCtx.EngineCtx engine : ship.engines()) {
            EngineFxState st = engine.st();
            if (st.runInit && engine.running() && !st.wasRunning) {
                st.startupTicks = 25;
            }
            st.wasRunning = engine.running();
            st.runInit = true;
            if (!engine.running()) {
                continue;
            }

            boolean startup = st.startupTicks > 0;
            if (startup) {
                st.startupTicks--;
            }
            double rate = (startup ? 1.3 : 0.35) * scale;
            int n = FxUtil.count(rng, rate);
            for (int i = 0; i < n; i++) {
                double jx = (rng.nextDouble() - 0.5) * 0.6;
                double jz = (rng.nextDouble() - 0.5) * 0.6;
                float shade = startup ? 0.28f + rng.nextFloat() * 0.12f
                        : 0.60f + rng.nextFloat() * 0.15f;
                FxUtil.smokeVapor(level, false, shade,
                        engine.pos().x + jx, engine.pos().y + 0.6, engine.pos().z + jz,
                        ship.velocity().x * 0.5 + (rng.nextDouble() - 0.5) * 0.01,
                        0.035 + rng.nextDouble() * 0.02 + ship.velocity().y * 0.5,
                        ship.velocity().z * 0.5 + (rng.nextDouble() - 0.5) * 0.01,
                        (startup ? 0.55f : 0.40f) + rng.nextFloat() * 0.2f,
                        30 + rng.nextInt(20), startup ? 0.6f : 0.45f, 1.3f);
            }
            // Occasional vanilla smoke wisp for texture variety.
            if (rng.nextFloat() < 0.15f * scale) {
                FxUtil.vanilla(level, false, ParticleTypes.SMOKE,
                        engine.pos().x, engine.pos().y + 0.7, engine.pos().z,
                        0.0, 0.04, 0.0);
            }
        }
    }

    private EngineFx() {}
}
