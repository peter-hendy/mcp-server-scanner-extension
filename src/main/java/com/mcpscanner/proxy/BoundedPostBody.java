package com.mcpscanner.proxy;

import com.mcpscanner.config.ResourceCap;
import fi.iki.elonen.NanoHTTPD;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.LongSupplier;

final class BoundedPostBody {

    private static final int CHUNK_BYTES = 8192;

    private final LongSupplier limitBytes;

    BoundedPostBody(LongSupplier limitBytes) {
        this.limitBytes = limitBytes;
    }

    String read(NanoHTTPD.IHTTPSession session) throws IOException {
        long declaredLength = declaredLength(session.getHeaders(), currentLimit());
        ByteArrayOutputStream body = readUpTo(session.getInputStream(), declaredLength);
        if (body.size() != declaredLength) {
            throw new IOException("POST body shorter than Content-Length");
        }
        return body.toString(StandardCharsets.UTF_8);
    }

    private long currentLimit() {
        long limit = limitBytes.getAsLong();
        if (!ResourceCap.PROXY_POST_BODY_BYTES.accepts(limit)) {
            throw new IllegalArgumentException("POST body limit must be between 1 and "
                    + ResourceCap.PROXY_POST_BODY_BYTES.maxValue() + " bytes");
        }
        return limit;
    }

    private static long declaredLength(Map<String, String> headers, long limit) throws IOException {
        if (headerValue(headers, "Transfer-Encoding") != null) throw new LengthRequiredException();
        String contentLength = headerValue(headers, "Content-Length");
        if (contentLength == null) throw new LengthRequiredException();
        long length = parseContentLength(contentLength);
        if (length > limit) throw new TooLargeException();
        return length;
    }

    private static ByteArrayOutputStream readUpTo(InputStream input, long length) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] chunk = new byte[CHUNK_BYTES];
        long remaining = length;
        while (remaining > 0) {
            int count = input.read(chunk, 0, (int) Math.min(chunk.length, remaining));
            if (count == -1) break;
            body.write(chunk, 0, count);
            remaining -= count;
        }
        return body;
    }

    private static String headerValue(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private static long parseContentLength(String value) throws IOException {
        try {
            long length = Long.parseLong(value);
            if (length < 0) throw new NumberFormatException();
            return length;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid Content-Length", e);
        }
    }

    static final class TooLargeException extends IOException {}

    static final class LengthRequiredException extends IOException {}
}
