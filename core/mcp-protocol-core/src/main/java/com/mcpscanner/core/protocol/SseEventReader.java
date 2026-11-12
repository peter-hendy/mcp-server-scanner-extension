package com.mcpscanner.core.protocol;

public final class SseEventReader implements McpEventSource {
    private static final int LINE_FEED = '\n';
    private static final int CARRIAGE_RETURN = '\r';

    private final java.io.InputStream stream;
    private final int maxBytes;
    private int eventBytes;
    private boolean afterCarriageReturn;

    public SseEventReader(java.io.InputStream stream, int maxBytes) {
        if (maxBytes <= 0) throw new IllegalArgumentException("positive bounds required");
        this.stream = new java.io.BufferedInputStream(java.util.Objects.requireNonNull(stream, "stream required"));
        this.maxBytes = maxBytes;
    }

    @Override
    public String nextEvent() {
        var event = new java.io.ByteArrayOutputStream();
        eventBytes = 0;
        boolean lineStart = true;
        for (int next = nextNormalizedByte(); next >= 0; next = nextNormalizedByte()) {
            if (next == LINE_FEED && lineStart && event.size() == 0) continue;
            event.write(next);
            if (next == LINE_FEED && lineStart) return event.toString(java.nio.charset.StandardCharsets.UTF_8);
            lineStart = next == LINE_FEED;
        }
        if (event.size() == 0) return null;
        throw new IllegalArgumentException("incomplete SSE response");
    }

    private int nextNormalizedByte() {
        int next = nextByte();
        if (next == LINE_FEED && afterCarriageReturn) next = nextByte();
        afterCarriageReturn = next == CARRIAGE_RETURN;
        return afterCarriageReturn ? LINE_FEED : next;
    }

    private int nextByte() {
        try {
            int next = stream.read();
            if (next >= 0 && ++eventBytes > maxBytes) throw new IllegalArgumentException("SSE event exceeds limit");
            return next;
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
