package com.mcpscanner.client;

import com.mcpscanner.mcp.BoundedBodyReader;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

final class ResponseBoundInputStream extends FilterInputStream {
    private long bytesRead;

    ResponseBoundInputStream(InputStream input) {
        super(input);
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value >= 0) count(1);
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int count = in.read(buffer, offset, length);
        if (count > 0) count(count);
        return count;
    }

    private void count(int count) throws IOException {
        bytesRead += count;
        if (bytesRead > BoundedBodyReader.MAX_RESPONSE_BYTES) {
            throw new IOException("SSE response exceeded response bound");
        }
    }
}
