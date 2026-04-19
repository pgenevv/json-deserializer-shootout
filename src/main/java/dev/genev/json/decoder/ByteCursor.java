package dev.genev.json.decoder;

/**
 * Stateful byte cursor shared by the hand-rolled decoders.
 *
 * Core design choices:
 * - Operates on {@code byte[]} directly; never materialises an intermediate
 *   {@link String} or {@link java.nio.CharBuffer}.
 * - Assumes ASCII input with no escape sequences. Market-data JSON routinely
 *   satisfies that.
 * - Fixed-schema consumption: callers advance field-by-field in the known
 *   order, so we never need a hash map of keys.
 * - Numbers are parsed by hand. Avoiding {@code Long.parseLong} / {@code
 *   Double.parseDouble} skips both the interim {@link String} allocation and
 *   the much slower general-purpose parser logic.
 */
public final class ByteCursor {

    byte[] src;
    int pos;
    int end;

    public void reset(byte[] src, int offset, int length) {
        this.src = src;
        this.pos = offset;
        this.end = offset + length;
    }

    public void skipWhitespace() {
        while (pos < end) {
            byte b = src[pos];
            if (b == ' ' || b == '\t' || b == '\n' || b == '\r') {
                pos++;
            } else {
                return;
            }
        }
    }

    public void expect(byte b) {
        skipWhitespace();
        if (pos >= end || src[pos] != b) {
            throw fail("expected '" + (char) b + "'");
        }
        pos++;
    }

    /**
     * Skip a JSON string literal without recording its content. Used to walk
     * past known field names in fixed-schema inputs.
     */
    public void skipString() {
        skipWhitespace();
        if (src[pos++] != '"') {
            throw fail("expected opening quote");
        }
        while (src[pos] != '"') {
            pos++;
        }
        pos++;
    }

    /**
     * Returns offset/length of the string contents (caller turns it into a
     * String or interns via {@link SymbolCache}).
     */
    public int readStringStart() {
        skipWhitespace();
        if (src[pos++] != '"') {
            throw fail("expected opening quote");
        }
        int start = pos;
        while (src[pos] != '"') {
            pos++;
        }
        int len = pos - start;
        pos++;
        stringOff = start;
        stringLen = len;
        return start;
    }

    public int stringOff;
    public int stringLen;

    public long readLong() {
        skipWhitespace();
        boolean neg = false;
        byte b = src[pos];
        if (b == '-') {
            neg = true;
            pos++;
        }
        long r = 0;
        while (pos < end) {
            b = src[pos];
            if (b >= '0' && b <= '9') {
                r = r * 10 + (b - '0');
                pos++;
            } else {
                break;
            }
        }
        return neg ? -r : r;
    }

    public double readDouble() {
        skipWhitespace();
        boolean neg = false;
        byte b = src[pos];
        if (b == '-') {
            neg = true;
            pos++;
        }
        long intPart = 0;
        while (pos < end) {
            b = src[pos];
            if (b >= '0' && b <= '9') {
                intPart = intPart * 10 + (b - '0');
                pos++;
            } else {
                break;
            }
        }
        double result = intPart;
        if (pos < end && src[pos] == '.') {
            pos++;
            long fracPart = 0;
            long fracScale = 1;
            while (pos < end) {
                b = src[pos];
                if (b >= '0' && b <= '9') {
                    fracPart = fracPart * 10 + (b - '0');
                    fracScale *= 10;
                    pos++;
                } else {
                    break;
                }
            }
            result += (double) fracPart / (double) fracScale;
        }
        if (pos < end && (src[pos] == 'e' || src[pos] == 'E')) {
            pos++;
            boolean eneg = false;
            if (src[pos] == '+') {
                pos++;
            } else if (src[pos] == '-') {
                eneg = true;
                pos++;
            }
            int exp = 0;
            while (pos < end) {
                b = src[pos];
                if (b >= '0' && b <= '9') {
                    exp = exp * 10 + (b - '0');
                    pos++;
                } else {
                    break;
                }
            }
            double mult = 1.0;
            for (int i = 0; i < exp; i++) {
                mult *= 10.0;
            }
            result = eneg ? result / mult : result * mult;
        }
        return neg ? -result : result;
    }

    private IllegalStateException fail(String msg) {
        return new IllegalStateException(msg + " at offset " + pos);
    }
}
