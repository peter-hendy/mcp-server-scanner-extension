package com.mcpscanner.http.wire;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

final class LengthLimitedInputStream extends FilterInputStream {

    private long remaining;

    LengthLimitedInputStream(InputStream in, long length) {
        super(in);
        this.remaining = length;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int next = super.read();
        remaining = next == -1 ? 0 : remaining - 1;
        return next;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int read = super.read(buffer, offset, (int) Math.min(length, remaining));
        remaining = read == -1 ? 0 : remaining - read;
        return read;
    }
}
