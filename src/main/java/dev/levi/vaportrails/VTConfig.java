package dev.levi.vaportrails;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client TOML config: per-effect toggles, intensities and thresholds. */
public final class VTConfig {

    public static final ModConfigSpec SPEC;

    // ---- general ----
    public static final ModConfigSpec.DoubleValue MASTER_INTENSITY;
    public static final ModConfigSpec.IntValue MAX_PARTICLES;
    public static final ModConfigSpec.IntValue PER_SHIP_SPAWNS_PER_TICK;
    public static final ModConfigSpec.DoubleValue PROPELLER_BUDGET_SHARE;
    public static final ModConfigSpec.IntValue EFFECT_RANGE;
    public static final ModConfigSpec.BooleanValue DEBUG_MARKERS;
    public static final ModConfigSpec.BooleanValue ADAPTIVE_QUALITY;
    public static final ModConfigSpec.IntValue TARGET_FPS;
    public static final ModConfigSpec.BooleanValue AIRFLOW_ENABLED;
    public static final ModConfigSpec.BooleanValue HULL_SHAPE;

    // ---- assembly / disassembly ----
    public static final ModConfigSpec.BooleanValue ASSEMBLY_ENABLED;
    public static final ModConfigSpec.DoubleValue ASSEMBLY_INTENSITY;
    public static final ModConfigSpec.IntValue ASSEMBLY_RANGE;

    // ---- wake turbulence ----
    public static final ModConfigSpec.BooleanValue WAKE_TURB_ENABLED;
    public static final ModConfigSpec.DoubleValue WAKE_TURB_LIFETIME;
    public static final ModConfigSpec.DoubleValue WAKE_TURB_STRENGTH;
    public static final ModConfigSpec.BooleanValue WAKE_TURB_REVEAL;

    // ---- shock cone ----
    public static final ModConfigSpec.BooleanValue SHOCK_ENABLED;
    public static final ModConfigSpec.DoubleValue SHOCK_MIN_SPEED;
    public static final ModConfigSpec.DoubleValue SHOCK_INTENSITY;

    // ---- wingtip trails ----
    public static final ModConfigSpec.BooleanValue WINGTIP_ENABLED;
    public static final ModConfigSpec.DoubleValue WINGTIP_MIN_SPEED;
    public static final ModConfigSpec.DoubleValue WINGTIP_INTENSITY;

    // ---- propeller effects ----
    public static final ModConfigSpec.BooleanValue PROP_TIP_ENABLED;
    public static final ModConfigSpec.DoubleValue PROP_TIP_MIN_RPM;
    public static final ModConfigSpec.BooleanValue PROP_WASH_ENABLED;
    public static final ModConfigSpec.DoubleValue PROP_WASH_MIN_RPM;
    public static final ModConfigSpec.BooleanValue PROP_WASH_INVERT;
    public static final ModConfigSpec.BooleanValue PROP_GROUND_ENABLED;
    public static final ModConfigSpec.DoubleValue PROP_GROUND_RANGE;
    public static final ModConfigSpec.BooleanValue PROP_STARTUP_ENABLED;
    public static final ModConfigSpec.IntValue PROP_BLADES;
    public static final ModConfigSpec.DoubleValue PROP_INTENSITY;

    // ---- engine smoke ----
    public static final ModConfigSpec.BooleanValue ENGINE_ENABLED;
    public static final ModConfigSpec.DoubleValue ENGINE_INTENSITY;

    // ---- hover dust ----
    public static final ModConfigSpec.BooleanValue HOVER_ENABLED;
    public static final ModConfigSpec.DoubleValue HOVER_MAX_HEIGHT;
    public static final ModConfigSpec.DoubleValue HOVER_INTENSITY;

    // ---- water wake ----
    public static final ModConfigSpec.BooleanValue WAKE_ENABLED;
    public static final ModConfigSpec.DoubleValue WAKE_MIN_SPEED;
    public static final ModConfigSpec.DoubleValue WAKE_INTENSITY;

    // ---- cloud punch ----
    public static final ModConfigSpec.BooleanValue CLOUD_ENABLED;
    public static final ModConfigSpec.DoubleValue CLOUD_MIN_SPEED;
    public static final ModConfigSpec.DoubleValue CLOUD_INTENSITY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("general");
        MASTER_INTENSITY = b.comment("Master multiplier applied to every effect's particle output.")
                .defineInRange("masterIntensity", 1.0, 0.0, 2.0);
        MAX_PARTICLES = b.comment("Global cap on live Vapor Trails particles.")
                .defineInRange("maxParticles", 2000, 100, 8000);
        PER_SHIP_SPAWNS_PER_TICK = b.comment("Cap on particles spawned per ship per tick.")
                .defineInRange("perShipSpawnsPerTick", 90, 5, 400);
        PROPELLER_BUDGET_SHARE = b.comment(
                        "Share of a ship's per-tick particle budget reserved for propeller effects,",
                        "so many-propeller ships don't starve wingtip/wake effects.")
                .defineInRange("propellerBudgetShare", 0.6, 0.1, 0.9);
        EFFECT_RANGE = b.comment("Ships farther than this from the camera spawn no effects (blocks).")
                .defineInRange("effectRange", 192, 32, 512);
        DEBUG_MARKERS = b.comment(
                        "Spawn end-rod marker particles at tracked ship centres and propeller",
                        "hubs/tips. Useful to verify Sable tracking and propeller detection.")
                .define("debugMarkers", false);
        ADAPTIVE_QUALITY = b.comment(
                        "Automatically thin out effects when the frame rate drops below",
                        "targetFps, and restore them once there is headroom again.")
                .define("adaptiveQuality", true);
        TARGET_FPS = b.comment("Frame rate the adaptive governor tries to protect.")
                .defineInRange("targetFps", 45, 15, 240);
        AIRFLOW_ENABLED = b.comment(
                        "Simulate a shared airflow field so propeller slipstreams push",
                        "nearby vapor, dust and foam around instead of every effect",
                        "drifting independently. Turn off for the old static behaviour.")
                .define("airflowSimulation", true);
        HULL_SHAPE = b.comment(
                        "Scan each ship's blocks to find its real silhouette, so wingtip",
                        "trails come off the actual wingtips and hover dust only rises",
                        "from ground genuinely under the hull. Turning this off falls back",
                        "to the bounding box, which is cheaper but boxy on odd airframes.")
                .define("hullShapeScan", true);
        b.pop();

        b.push("wingtipTrails");
        WINGTIP_ENABLED = b.comment("White condensation streaks from a fast ship's outer edges.")
                .define("enabled", true);
        WINGTIP_MIN_SPEED = b.comment("Minimum ship speed for wingtip trails (m/s = blocks per second).")
                .defineInRange("minSpeed", 20.0, 1.0, 100.0);
        WINGTIP_INTENSITY = b.defineInRange("intensity", 1.0, 0.0, 2.0);
        b.pop();

        b.push("propeller");
        PROP_TIP_ENABLED = b.comment("Blade-tip vapor ring on fast-spinning propellers.")
                .define("bladeTipVapor", true);
        PROP_TIP_MIN_RPM = b.comment("Minimum rotation speed (Create RPM) for blade-tip vapor.")
                .defineInRange("bladeTipMinRpm", 64.0, 1.0, 256.0);
        PROP_WASH_ENABLED = b.comment("Cone of faint air-swirl particles streaming behind the disc.")
                .define("propWash", true);
        PROP_WASH_MIN_RPM = b.comment("Minimum rotation speed (Create RPM) for prop wash.")
                .defineInRange("propWashMinRpm", 32.0, 1.0, 256.0);
        PROP_WASH_INVERT = b.comment(
                        "Flip the prop wash direction. Use this if the air stream",
                        "visibly blows out of the front of your propellers.")
                .define("invertPropWash", false);
        PROP_GROUND_ENABLED = b.comment("Dust ring / water spray under a running propeller near the surface.")
                .define("groundDisturbance", true);
        PROP_GROUND_RANGE = b.comment("How far below a propeller disc the ground/water is disturbed (blocks).")
                .defineInRange("groundDisturbanceRange", 5.0, 1.0, 12.0);
        PROP_STARTUP_ENABLED = b.comment("Dense smoke/vapor burst when a propeller starts or stops.")
                .define("startupPuff", true);
        PROP_BLADES = b.comment(
                        "Assumed blade count for the tip-vapor flicker pattern.",
                        "(Blade count isn't exposed by Aeronautics, so the pass timing uses this.)")
                .defineInRange("assumedBladeCount", 3, 1, 8);
        PROP_INTENSITY = b.defineInRange("intensity", 1.0, 0.0, 2.0);
        b.pop();

        b.push("engineSmoke");
        ENGINE_ENABLED = b.comment("Light grey smoke from running engine blocks; denser on startup.")
                .define("enabled", true);
        ENGINE_INTENSITY = b.defineInRange("intensity", 1.0, 0.0, 2.0);
        b.pop();

        b.push("hoverDust");
        HOVER_ENABLED = b.comment("Ground dust/debris kicked up under a low-flying or hovering ship.")
                .define("enabled", true);
        HOVER_MAX_HEIGHT = b.comment("Maximum height above ground for hover dust (blocks).")
                .defineInRange("maxHeight", 4.0, 1.0, 10.0);
        HOVER_INTENSITY = b.defineInRange("intensity", 1.0, 0.0, 2.0);
        b.pop();

        b.push("waterWake");
        WAKE_ENABLED = b.comment("V-shaped foam trail behind a hull moving through water.")
                .define("enabled", true);
        WAKE_MIN_SPEED = b.comment("Minimum ship speed for a wake (m/s).")
                .defineInRange("minSpeed", 2.0, 0.5, 40.0);
        WAKE_INTENSITY = b.defineInRange("intensity", 1.0, 0.0, 2.0);
        b.pop();

        b.push("assembly");
        ASSEMBLY_ENABLED = b.comment(
                        "A burst of displaced air and ground dust when a structure tears",
                        "loose and becomes a ship, and a heavier settling plume when one is",
                        "taken apart. One-shot: nothing here repeats during normal flight.")
                .define("enabled", true);
        ASSEMBLY_INTENSITY = b.defineInRange("intensity", 1.0, 0.0, 2.0);
        ASSEMBLY_RANGE = b.comment(
                        "How close you must be for an assembly to register (blocks).",
                        "Ships appearing beyond this are streaming into view rather than",
                        "being built, and would be too far away to see anyway.")
                .defineInRange("triggerRange", 64, 8, 256);
        b.pop();

        b.push("wakeTurbulence");
        WAKE_TURB_ENABLED = b.comment(
                        "Fast ships shed counter-rotating vortices from their wingtips that",
                        "linger in the air long after the visible trail has faded. Flying",
                        "through one - yours or someone else's - tears your vapor apart.")
                .define("enabled", true);
        WAKE_TURB_LIFETIME = b.comment("How long shed turbulence lingers, in seconds.")
                .defineInRange("lifetimeSeconds", 20.0, 3.0, 60.0);
        WAKE_TURB_STRENGTH = b.comment("Multiplier on how violently wakes throw things around.")
                .defineInRange("strength", 1.0, 0.0, 2.0);
        WAKE_TURB_REVEAL = b.comment(
                        "Draw disturbed air as ragged vapor when a ship flies into an",
                        "existing wake. Turning this off leaves wakes invisible but still",
                        "physically active.")
                .define("reveal", true);
        b.pop();

        b.push("shockCone");
        SHOCK_ENABLED = b.comment("Condensation cone that forms around the hull at very high speed.")
                .define("enabled", true);
        SHOCK_MIN_SPEED = b.comment("Speed at which the shock cone starts to form (m/s).")
                .defineInRange("minSpeed", 40.0, 10.0, 200.0);
        SHOCK_INTENSITY = b.defineInRange("intensity", 1.0, 0.0, 2.0);
        b.pop();

        b.push("cloudPunch");
        CLOUD_ENABLED = b.comment("Mist burst ring when a ship punches through the cloud layer at speed.")
                .define("enabled", true);
        CLOUD_MIN_SPEED = b.comment("Minimum vertical speed to trigger a cloud punch (m/s).")
                .defineInRange("minVerticalSpeed", 5.0, 0.5, 60.0);
        CLOUD_INTENSITY = b.defineInRange("intensity", 1.0, 0.0, 2.0);
        b.pop();

        SPEC = b.build();
    }

    private VTConfig() {}
}
