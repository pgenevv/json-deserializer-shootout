package dev.genev.json;

/**
 * One level of the order book. Intentionally mutable so a fixed-size
 * Level[] can be reused across decodes.
 */
public final class Level {
    public double price;
    public long size;

    public Level() {}
}
