package dev.levi.vaportrails.fx;

/** Mutable per-ship effect state that persists across ticks. */
public final class ShipFxState {
    /** Ship centre Y last tick, for cloud-layer crossing detection. */
    public double prevY;
    public boolean prevYInit;
    /** Cooldown so one crossing doesn't fire several bursts. */
    public int cloudCooldown;
    /** Ticks until this ship sheds its next pair of wake vortices. */
    public int wakeShedIn;
    /** Last world position each wingtip shed a vortex from, for spacing. */
    public net.minecraft.world.phys.Vec3 lastShedA;
    public net.minecraft.world.phys.Vec3 lastShedB;
    /** Smoothed turbulence the ship is currently flying through. */
    public double turbulence;
}
