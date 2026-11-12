package com.mcpscanner.proxy;

import com.mcpscanner.mcp.BoundedBodyReader;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

final class CapturedBody extends FilterInputStream {

    private final ByteArrayOutputStream copy = new ByteArrayOutputStream();

    CapturedBody(InputStream body) {
        super(body);
    }

    @Override
    public int read() throws IOException {
        int read = super.read();
        if (read >= 0) {
            keep(new byte[]{(byte) read}, 0, 1);
        }
        return read;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = super.read(buffer, offset, length);
        if (read > 0) {
            keep(buffer, offset, read);
        }
        return read;
    }

    synchronized byte[] captured() {
        return copy.toByteArray();
    }

    private synchronized void keep(byte[] bytes, int offset, int length) {
        int room = BoundedBodyReader.MAX_RESPONSE_BYTES - copy.size();
        copy.write(bytes, offset, Math.min(room, length));
    }
}
