package dev.genev.json.decoder;

import dev.genev.json.Level;
import dev.genev.json.OrderBook;

/**
 * Fixed-schema byte decoder for the order-book payload.
 *
 * Expected field order at the top level: symbol, ts, seq, bids, asks.
 * Each level is {@code {"price":D,"size":L}} in that order.
 *
 * Writes back into a caller-supplied {@link OrderBook} whose {@link
 * OrderBook#bids} and {@link OrderBook#asks} arrays are already populated
 * with mutable {@link Level} instances, so the hot path does not allocate
 * anything but the occasional unknown-symbol String (see {@link
 * SymbolCache}).
 */
public final class ByteOrderBookDecoder {

    private final ByteCursor cur = new ByteCursor();
    private final SymbolCache symbols = new SymbolCache();

    public void decode(byte[] src, OrderBook out) {
        ByteCursor c = cur;
        c.reset(src, 0, src.length);

        c.expect((byte) '{');

        c.skipString();              // "symbol"
        c.expect((byte) ':');
        c.readStringStart();
        out.symbol = symbols.intern(src, c.stringOff, c.stringLen);
        c.expect((byte) ',');

        c.skipString();              // "ts"
        c.expect((byte) ':');
        out.ts = c.readLong();
        c.expect((byte) ',');

        c.skipString();              // "seq"
        c.expect((byte) ':');
        out.seq = c.readLong();
        c.expect((byte) ',');

        c.skipString();              // "bids"
        c.expect((byte) ':');
        readLevels(c, out.bids);
        c.expect((byte) ',');

        c.skipString();              // "asks"
        c.expect((byte) ':');
        readLevels(c, out.asks);

        c.expect((byte) '}');
    }

    private static void readLevels(ByteCursor c, Level[] target) {
        c.expect((byte) '[');
        for (int i = 0; i < target.length; i++) {
            if (i > 0) {
                c.expect((byte) ',');
            }
            Level lvl = target[i];
            c.expect((byte) '{');
            c.skipString();          // "price"
            c.expect((byte) ':');
            lvl.price = c.readDouble();
            c.expect((byte) ',');
            c.skipString();          // "size"
            c.expect((byte) ':');
            lvl.size = c.readLong();
            c.expect((byte) '}');
        }
        c.expect((byte) ']');
    }
}
