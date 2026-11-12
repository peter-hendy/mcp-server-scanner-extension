package com.mcpscanner.http.wire;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

final class ChunkedInputStream extends InputStream {

    private static final Pattern HEX_DIGITS = Pattern.compile("[0-9A-Fa-f]+");

    private final InputStream in;
    private long chunkRemaining;
    private boolean chunkEndPending;
    private boolean finished;

    ChunkedInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public int read() throws IOException {
        byte[] single = new byte[1];
        return read(single, 0, 1) == -1 ? -1 : single[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (!hasChunkData()) {
            return -1;
        }
        int read = in.read(buffer, offset, (int) Math.min(length, chunkRemaining));
        if (read == -1) {
            throw new EOFException("connection closed inside a chunk");
        }
        chunkRemaining -= read;
        chunkEndPending = chunkRemaining == 0;
        return read;
    }

    private boolean hasChunkData() throws IOException {
        if (finished) {
            return false;
        }
        if (chunkRemaining == 0) {
            if (chunkEndPending) {
                WireLines.read(in);
                chunkEndPending = false;
            }
            chunkRemaining = nextChunkSize();
            if (chunkRemaining == 0) {
                skipTrailers();
                finished = true;
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private long nextChunkSize() throws IOException {
        String line = WireLines.read(in);
        if (line == null) {
            throw new EOFException("connection closed before the last chunk");
        }
        int extension = line.indexOf(';');
        String size = (extension >= 0 ? line.substring(0, extension) : line).trim();
        if (!HEX_DIGITS.matcher(size).matches()) {
            throw malformedChunkSize(line);
        }
        try {
            return Long.parseLong(size, 16);
        } catch (NumberFormatException overflowing) {
            throw malformedChunkSize(line);
        }
    }

    private static IOException malformedChunkSize(String line) {
        return new IOException("malformed chunk size: " + line);
    }

    private void skipTrailers() throws IOException {
        HeaderSection.read(in);
    }
}
