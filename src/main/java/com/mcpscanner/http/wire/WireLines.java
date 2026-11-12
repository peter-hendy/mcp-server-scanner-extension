package com.mcpscanner.http.wire;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class WireLines {

    private static final int MAX_LINE_BYTES = 64 * 1024;

    private WireLines() {
    }

    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int next = in.read(); next != -1; next = in.read()) {
            if (next == '\n') {
                return withoutCarriageReturn(line.toString(StandardCharsets.ISO_8859_1));
            }
            if (line.size() == MAX_LINE_BYTES) {
                throw new IOException("protocol line longer than " + MAX_LINE_BYTES + " bytes");
            }
            line.write(next);
        }
        return line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1);
    }

    private static String withoutCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
