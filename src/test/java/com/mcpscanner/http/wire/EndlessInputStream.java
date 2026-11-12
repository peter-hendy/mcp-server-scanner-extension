package com.mcpscanner.http.wire;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class EndlessInputStream extends InputStream {

    private final byte[] prefix;
    private final byte[] repeated;
    private long position;

    private EndlessInputStream(String prefix, String repeated) {
        this.prefix = prefix.getBytes(StandardCharsets.ISO_8859_1);
        this.repeated = repeated.getBytes(StandardCharsets.ISO_8859_1);
    }

    static InputStream repeating(String prefix, String repeated) {
        return new EndlessInputStream(prefix, repeated);
    }

    @Override
    public int read() {
        long next = position++;
        if (next < prefix.length) {
            return prefix[(int) next] & 0xff;
        }
        return repeated[(int) ((next - prefix.length) % repeated.length)] & 0xff;
    }
}
