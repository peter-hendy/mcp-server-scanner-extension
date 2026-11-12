package com.mcpscanner.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class ChunkedBody {

    private static final String CHUNKED = "chunked";
    private static final int MAX_SIZE_DIGITS = 8;
    private static final long INVALID_SIZE = -1;

    private final byte[] framed;
    private final ByteArrayOutputStream decoded = new ByteArrayOutputStream();
    private int position;

    private ChunkedBody(byte[] framed) {
        this.framed = framed;
    }

    public static byte[] unframe(List<String> transferEncodings, byte[] body) {
        return declaresChunked(transferEncodings) ? new ChunkedBody(body).decode().orElse(body) : body;
    }

    private static boolean declaresChunked(List<String> transferEncodings) {
        List<String> codings = transferEncodings.stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(coding -> !coding.isEmpty())
                .toList();
        return !codings.isEmpty() && codings.get(codings.size() - 1).equalsIgnoreCase(CHUNKED);
    }

    private Optional<byte[]> decode() {
        while (true) {
            long size = nextChunkSize();
            if (size == INVALID_SIZE) return Optional.empty();
            if (size == 0) return trailersEndTheBody() ? Optional.of(decoded.toByteArray()) : Optional.empty();
            if (!copyChunk(size)) return Optional.empty();
        }
    }

    private long nextChunkSize() {
        return nextLine().map(ChunkedBody::parseSize).orElse(INVALID_SIZE);
    }

    private static long parseSize(String sizeLine) {
        int extensionStart = sizeLine.indexOf(';');
        String digits = (extensionStart < 0 ? sizeLine : sizeLine.substring(0, extensionStart)).stripTrailing();
        if (digits.isEmpty() || digits.length() > MAX_SIZE_DIGITS || !isHex(digits)) return INVALID_SIZE;
        return Long.parseLong(digits, 16);
    }

    private static boolean isHex(String digits) {
        return digits.chars().allMatch(c -> Character.digit(c, 16) >= 0);
    }

    private boolean copyChunk(long size) {
        if (size > framed.length - position) return false;
        decoded.write(framed, position, (int) size);
        position += (int) size;
        return nextLine().filter(String::isEmpty).isPresent();
    }

    private boolean trailersEndTheBody() {
        Optional<String> line;
        while ((line = nextLine()).isPresent()) {
            if (line.get().isEmpty()) return position == framed.length;
            if (line.get().indexOf(':') <= 0) return false;
        }
        return false;
    }

    private Optional<String> nextLine() {
        for (int end = position; end + 1 < framed.length; end++) {
            if (framed[end] == '\r' && framed[end + 1] == '\n') {
                String line = new String(framed, position, end - position, StandardCharsets.ISO_8859_1);
                position = end + 2;
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }
}
