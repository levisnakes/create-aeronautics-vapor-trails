package dev.levi.vaportrails.fx;

/** Mutable per-engine effect state that persists across ticks. */
public final class EngineFxState {
    public boolean wasRunning;
    public boolean runInit;
    /** Ticks of extra-dense startup smoke remaining. */
    public int startupTicks;
}
