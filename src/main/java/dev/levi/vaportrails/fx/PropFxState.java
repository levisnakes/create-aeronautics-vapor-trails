package dev.levi.vaportrails.fx;

/** Mutable per-propeller effect state that persists across ticks. */
public final class PropFxState {
    /** Blade rotation phase accumulator, radians. */
    public double phase;
    public double prevRpm;
    public boolean wasSpinning;
    public boolean spinInit;
}
