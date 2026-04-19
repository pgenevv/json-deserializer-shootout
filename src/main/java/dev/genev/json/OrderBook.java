package dev.genev.json;

/**
 * Order-book snapshot with a fixed depth of 50 levels per side.
 * Arrays are pre-allocated so the hand-rolled decoder can rewrite their
 * contents without any fresh allocations.
 */
public final class OrderBook {
    public static final int DEPTH = 100;

    public String symbol;
    public long ts;
    public long seq;
    public Level[] bids;
    public Level[] asks;

    public OrderBook() {
        this.bids = newSide();
        this.asks = newSide();
    }

    private static Level[] newSide() {
        Level[] side = new Level[DEPTH];
        for (int i = 0; i < DEPTH; i++) {
            side[i] = new Level();
        }
        return side;
    }
}
