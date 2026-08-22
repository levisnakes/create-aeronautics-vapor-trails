package dev.levi.vaportrails.fx;

import dev.levi.vaportrails.VTConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Adaptive quality governor.
 *
 * A fleet of propeller-heavy ships can ask for far more particles than a given
 * machine can draw. Rather than making every user hand-tune intensities, this
 * watches the frame rate and quietly scales emission down when the client is
 * struggling, then eases back up once there is headroom again.
 *
 * Recovery is deliberately slower than cutback so the effect density does not
 * visibly pulse.
 */
public final class Quality {

    /** Never throttle below this share of the configured intensity. */
    private static final double FLOOR = 0.25;
    private static final double CUT_RATE = 0.06;
    private static final double RECOVER_RATE = 0.012;

    private static double factor = 1.0;

    public static void tick(Minecraft mc) {
        if (!VTConfig.ADAPTIVE_QUALITY.get()) {
            factor = 1.0;
            return;
        }
        int target = VTConfig.TARGET_FPS.get();
        int fps = mc.getFps();

        // Ignore the nonsense readings during loading screens and pauses.
        if (fps <= 0) {
            return;
        }

        if (fps < target) {
            // Cut harder the further below target we are.
            double deficit = Mth.clamp((target - fps) / (double) target, 0.0, 1.0);
            factor -= CUT_RATE * deficit;
        } else if (fps > target * 1.15) {
            factor += RECOVER_RATE;
        }
        factor = Mth.clamp(factor, FLOOR, 1.0);
    }

    /** Current throttle, 0.25 to 1.0. */
    public static double factor() {
        return factor;
    }

    public static void reset() {
        factor = 1.0;
    }

    private Quality() {}
}
