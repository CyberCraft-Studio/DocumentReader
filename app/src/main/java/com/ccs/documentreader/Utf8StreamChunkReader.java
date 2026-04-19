package com.ccs.documentreader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Послідовне читання UTF-8 з {@link InputStream} блоками сиріх байтів.
 */
final class Utf8StreamChunkReader {

    static final class Result {
        /** Може бути порожнім рядком; {@code null} означає, що даних більше немає. */
        final String text;
        /** {@code true}, якщо це останній блок (потік закритий / EOF). */
        final boolean endOfStream;

        Result(String text, boolean endOfStream) {
            this.text = text;
            this.endOfStream = endOfStream;
        }
    }

    private final InputStream in;
    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);

    /** Неповний UTF-8 на межі чанка (довжина довільна, не лише 4 байти). */
    private byte[] carry = new byte[0];
    private boolean closed = false;

    Utf8StreamChunkReader(InputStream in) {
        this.in = in;
    }

    /**
     * Читає до {@code maxRawBytes} нових байтів з потоку, декодує в текст.
     * Якщо повертає {@code null} — читання завершено (викликайте більше не потрібен).
     */
    Result readChunk(int maxRawBytes) throws IOException {
        if (closed) return null;

        byte[] buf = new byte[maxRawBytes];
        int got = 0;
        while (got < maxRawBytes) {
            int n = in.read(buf, got, maxRawBytes - got);
            if (n < 0) break;
            got += n;
        }

        boolean eof = got < maxRawBytes;
        int carryLen = carry.length;
        if (got <= 0 && carryLen <= 0) {
            closed = true;
            return null;
        }

        byte[] combined = new byte[carryLen + got];
        System.arraycopy(carry, 0, combined, 0, carryLen);
        System.arraycopy(buf, 0, combined, carryLen, got);
        int total = combined.length;

        int flushLen;
        if (eof) {
            flushLen = total;
            carry = new byte[0];
        } else {
            flushLen = trimToCompleteUtf8(combined, total);
            if (flushLen < total) {
                carry = Arrays.copyOfRange(combined, flushLen, total);
            } else {
                carry = new byte[0];
            }
        }

        if (flushLen <= 0 && eof) {
            closed = true;
            return new Result("", true);
        }
        if (flushLen <= 0) {
            return new Result("", false);
        }

        ByteBuffer bb = ByteBuffer.wrap(combined, 0, flushLen);
        CharBuffer cb = CharBuffer.allocate(Math.max(16, flushLen));
        decoder.decode(bb, cb, eof);
        if (eof) {
            decoder.flush(cb);
        }
        cb.flip();
        String text = cb.toString();
        if (eof) closed = true;
        return new Result(text, eof);
    }

    void closeQuietly() {
        try { in.close(); } catch (Exception ignored) {}
        closed = true;
    }

    private static int trimToCompleteUtf8(byte[] b, int len) {
        if (len <= 0) return 0;
        int i = len - 1;
        while (i >= 0 && (b[i] & 0xC0) == 0x80) {
            i--;
        }
        if (i < 0) return 0;
        int lead = b[i] & 0xFF;
        int need;
        if ((lead & 0x80) == 0) {
            need = 1;
        } else if ((lead & 0xE0) == 0xC0) {
            need = 2;
        } else if ((lead & 0xF0) == 0xE0) {
            need = 3;
        } else if ((lead & 0xF8) == 0xF0) {
            need = 4;
        } else {
            need = 1;
        }
        int have = len - i;
        if (have < need) {
            return i;
        }
        return len;
    }
}
