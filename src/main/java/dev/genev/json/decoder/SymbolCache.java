package dev.genev.json.decoder;

import java.nio.charset.StandardCharsets;

/**
 * Tiny open-addressed hash table that maps a byte-slice to a canonical String
 * instance so the hot path never allocates a String for known symbols.
 *
 * Seeded with a handful of common crypto pairs. Unknown symbols fall through
 * to a one-off {@link String} allocation; that's a deliberate trade-off so the
 * decoder stays correct when it sees something new, while paying zero for the
 * 99.9% case of seeing something it's already seen.
 */
public final class SymbolCache {
    private static final int CAPACITY = 16;
    private static final int MASK = CAPACITY - 1;

    private final byte[][] keys = new byte[CAPACITY][];
    private final String[] values = new String[CAPACITY];

    public SymbolCache() {
        String[] seed = {
                "BTC-USD", "ETH-USD", "SOL-USD", "DOGE-USD", "XRP-USD",
                "LTC-USD", "ADA-USD", "AVAX-USD"
        };
        for (String s : seed) {
            byte[] b = s.getBytes(StandardCharsets.US_ASCII);
            put(b, 0, b.length, s);
        }
    }

    private void put(byte[] src, int off, int len, String canonical) {
        int h = hash(src, off, len) & MASK;
        while (keys[h] != null) {
            h = (h + 1) & MASK;
        }
        byte[] copy = new byte[len];
        System.arraycopy(src, off, copy, 0, len);
        keys[h] = copy;
        values[h] = canonical;
    }

    public String intern(byte[] src, int off, int len) {
        int h = hash(src, off, len) & MASK;
        for (int probe = 0; probe < CAPACITY; probe++) {
            int idx = (h + probe) & MASK;
            byte[] k = keys[idx];
            if (k == null) break;
            if (k.length == len && matches(k, src, off, len)) {
                return values[idx];
            }
        }
        return new String(src, off, len, StandardCharsets.US_ASCII);
    }

    private static boolean matches(byte[] a, byte[] b, int bOff, int len) {
        for (int i = 0; i < len; i++) {
            if (a[i] != b[bOff + i]) return false;
        }
        return true;
    }

    private static int hash(byte[] b, int off, int len) {
        int h = 0;
        for (int i = 0; i < len; i++) {
            h = h * 31 + b[off + i];
        }
        return h;
    }
}
