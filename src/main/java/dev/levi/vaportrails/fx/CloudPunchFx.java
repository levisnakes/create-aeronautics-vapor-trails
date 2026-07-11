package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.VTConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Effect 6: cloud punch. A burst ring of mist when a ship crosses the vanilla
 * cloud layer at speed. The cloud height is read from the client dimension
 * effects, so render-distance or dimension quirks are respected.
 */
public final class CloudPunchFx {

    /** Vanilla clouds are rendered roughly this many blocks thick. */
    private static final double CLOUD_THICKNESS = 4.0;

    public static void tick(ClientLevel level, ShipCtx ship, RandomSource rng) {
        ShipFxState st = ship.st();
        if (st.cloudCooldown > 0) {
            st.cloudCooldown--;
        }
        if (!VTConfig.CLOUD_ENABLED.get()) {
            return;
        }
        float cloudY = level.effects().getCloudHeight();
        if (Float.isNaN(cloudY)) {
            return; // dimension without clouds
        }
        double scale = Budget.emissionScale() * VTConfig.CLOUD_INTENSITY.get();

        double y = ship.center().y;
        double prevY = st.prevYInit ? st.prevY : y;
        st.prevY = y;
        st.prevYInit = true;
        if (scale <= 0.0 || st.cloudCooldown > 0) {
            return;
        }

        double bandLo = cloudY;
        double bandHi = cloudY + CLOUD_THICKNESS;
        // Did the segment [prevY, y] touch the cloud band edge this tick?
        double lo = Math.min(prevY, y);
        double hi = Math.max(prevY, y);
        boolean crossedLo = lo <= bandLo && hi >= bandLo;
        boolean crossedHi = lo <= bandHi && hi >= bandHi;
        if (!crossedLo && !crossedHi) {
            return;
        }
        double vyMs = Math.abs(ship.velocity().y) * 20.0;
        if (vyMs < VTConfig.CLOUD_MIN_SPEED.get()) {
            return;
        }

        double crossY = crossedLo == (y > prevY) ? bandLo : bandHi;
        double halfWidth = Math.max(
                (ship.bounds().maxX - ship.bounds().minX),
                (ship.bounds().maxZ - ship.bounds().minZ)) * 0.5;
        double ringR = halfWidth * 1.2 + 1.0;
        double sizeF = Mth.clamp(halfWidth / 6.0, 0.5, 2.0);

        int n = FxUtil.count(rng, 40.0 * sizeF * scale);
        for (int i = 0; i < n; i++) {
            double angle = rng.nextDouble() * Mth.TWO_PI;
            double r = ringR * (0.8 + rng.nextDouble() * 0.4);
            Vec3 pos = new Vec3(
                    ship.center().x + Math.cos(angle) * r,
                    crossY + (rng.nextDouble() - 0.5) * 1.5,
                    ship.center().z + Math.sin(angle) * r);
            double out = 0.15 + rng.nextDouble() * 0.20;
            FxUtil.vapor(level, false, pos.x, pos.y, pos.z,
                    Math.cos(angle) * out + ship.velocity().x * 0.15,
                    (rng.nextDouble() - 0.5) * 0.04,
                    Math.sin(angle) * out + ship.velocity().z * 0.15,
                    2.2f + rng.nextFloat() * 1.5f, 35 + rng.nextInt(30), 0.6f, 2.4f);
        }
        st.cloudCooldown = 40;
    }

    private CloudPunchFx() {}
}
