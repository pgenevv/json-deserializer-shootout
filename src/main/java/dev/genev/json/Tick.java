package dev.genev.json;

/**
 * Mutable POJO for a single order-book tick.
 * Public fields + no-arg constructor so all five contenders can populate it
 * without reflection tricks or custom factories.
 */
public final class Tick {
    public String symbol;
    public double bid;
    public double ask;
    public long bidSize;
    public long askSize;
    public long ts;
    public long seq;

    public Tick() {}
}
