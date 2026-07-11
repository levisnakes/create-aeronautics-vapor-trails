package dev.levi.vaportrails.fx;

/** Mutable per-ship effect state that persists across ticks. */
public final class ShipFxState {
    /** Ship centre Y last tick, for cloud-layer crossing detection. */
    public double prevY;
    public boolean prevYInit;
    /** Cooldown so one crossing doesn't fire several bursts. */
    public int cloudCooldown;
}
