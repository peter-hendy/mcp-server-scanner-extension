package com.mcpscanner.http.wire;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record WireResponse(int status, List<WireHeader> headers, InputStream body) {

    private static final int SWITCHING_PROTOCOLS = 101;
    private static final int NO_CONTENT = 204;
    private static final int NOT_MODIFIED = 304;
    private static final int MAX_INTERIM_RESPONSES = 16;

    static WireResponse read(InputStream in, String requestMethod) throws IOException {
        for (int interim = 0; interim <= MAX_INTERIM_RESPONSES; interim++) {
            int status = statusOf(WireLines.read(in));
            List<WireHeader> headers = headersOf(in);
            if (status / 100 != 1 || status == SWITCHING_PROTOCOLS) {
                return new WireResponse(status, headers, bodyOf(status, headers, in, requestMethod));
            }
        }
        throw new IOException("more than " + MAX_INTERIM_RESPONSES + " interim responses");
    }

    public Optional<String> headerValue(String name) {
        return value(headers, name);
    }

    private static int statusOf(String statusLine) throws IOException {
        if (statusLine == null) {
            throw new EOFException("connection closed before a status line");
        }
        String[] parts = statusLine.split(" ", 3);
        try {
            return Integer.parseInt(parts[1]);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException malformed) {
            throw new IOException("malformed status line: " + statusLine);
        }
    }

    private static List<WireHeader> headersOf(InputStream in) throws IOException {
        List<WireHeader> headers = new ArrayList<>();
        for (String line : HeaderSection.read(in)) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.add(new WireHeader(line.substring(0, colon).trim(), line.substring(colon + 1).trim()));
            }
        }
        return List.copyOf(headers);
    }

    private static InputStream bodyOf(int status, List<WireHeader> headers, InputStream in, String requestMethod) {
        if ("HEAD".equalsIgnoreCase(requestMethod) || status == NO_CONTENT || status == NOT_MODIFIED) {
            return InputStream.nullInputStream();
        }
        if (value(headers, "transfer-encoding").map(coding -> coding.toLowerCase(Locale.ROOT).contains("chunked")).orElse(false)) {
            return new ChunkedInputStream(in);
        }
        return value(headers, "content-length")
                .<InputStream>map(length -> new LengthLimitedInputStream(in, contentLength(length)))
                .orElse(in);
    }

    private static long contentLength(String declared) {
        try {
            return Long.parseLong(declared.trim());
        } catch (NumberFormatException malformed) {
            throw new MalformedResponseException("malformed Content-Length: " + declared);
        }
    }

    private static Optional<String> value(List<WireHeader> headers, String name) {
        return headers.stream().filter(header -> header.named(name)).map(WireHeader::value).findFirst();
    }
}
