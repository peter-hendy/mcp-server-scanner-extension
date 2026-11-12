package com.mcpscanner.http.wire;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class HeaderSection {

    static final int MAX_BYTES = 64 * 1024;

    private HeaderSection() {
    }

    static List<String> read(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        long size = 0;
        for (String line = WireLines.read(in); line != null && !line.isEmpty(); line = WireLines.read(in)) {
            size += line.length();
            if (size > MAX_BYTES) {
                throw new IOException("header section longer than " + MAX_BYTES + " bytes");
            }
            lines.add(line);
        }
        return lines;
    }
}
