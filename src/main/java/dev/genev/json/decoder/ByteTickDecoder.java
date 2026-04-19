package dev.genev.json.decoder;

import dev.genev.json.Tick;

/**
 * Fixed-schema byte decoder for the tick payload.
 *
 * Expected input (field order is fixed):
 * {@code {"symbol":"BTC-USD","bid":67432.15,"ask":67432.85,"bidSize":215,"askSize":188,"ts":1729369812345,"seq":98765432}}
 *
 * Populates a caller-supplied {@link Tick} instance in place. Allocates
 * nothing for symbols that are already in the cache, which in a real feed
 * covers essentially 100% of messages.
 */
public final class ByteTickDecoder {

    private final ByteCursor cur = new ByteCursor();
    private final SymbolCache symbols = new SymbolCache();

    public void decode(byte[] src, Tick out) {
        decode(src, 0, src.length, out);
    }

    public void decode(byte[] src, int offset, int length, Tick out) {
        ByteCursor c = cur;
        c.reset(src, offset, length);

        c.expect((byte) '{');

        c.skipString();              // "symbol"
        c.expect((byte) ':');
        c.readStringStart();
        out.symbol = symbols.intern(src, c.stringOff, c.stringLen);
        c.expect((byte) ',');

        c.skipString();              // "bid"
        c.expect((byte) ':');
        out.bid = c.readDouble();
        c.expect((byte) ',');

        c.skipString();              // "ask"
        c.expect((byte) ':');
        out.ask = c.readDouble();
        c.expect((byte) ',');

        c.skipString();              // "bidSize"
        c.expect((byte) ':');
        out.bidSize = c.readLong();
        c.expect((byte) ',');

        c.skipString();              // "askSize"
        c.expect((byte) ':');
        out.askSize = c.readLong();
        c.expect((byte) ',');

        c.skipString();              // "ts"
        c.expect((byte) ':');
        out.ts = c.readLong();
        c.expect((byte) ',');

        c.skipString();              // "seq"
        c.expect((byte) ':');
        out.seq = c.readLong();
        c.expect((byte) '}');
    }
}
