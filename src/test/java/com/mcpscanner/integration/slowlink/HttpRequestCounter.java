package com.mcpscanner.integration.slowlink;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HttpRequestCounter implements ChunkObserver {

    private static final Pattern CONTENT_LENGTH = Pattern.compile("(?im)^content-length:\\s*(\\d+)\\s*$");
    private static final int HEAD_END = ('\r' << 24) | ('\n' << 16) | ('\r' << 8) | '\n';
    private static final int LINE_END = ('\r' << 8) | '\n';

    private final Runnable onRequest;
    private final ByteArrayOutputStream head = new ByteArrayOutputStream();
    private long bodyBytesRemaining;
    private int lastFourBytes;

    HttpRequestCounter(Runnable onRequest) {
        this.onRequest = onRequest;
    }

    @Override
    public void observe(byte[] chunk, int length) {
        int offset = 0;
        while (offset < length) {
            offset = bodyBytesRemaining > 0 ? skipBody(length, offset) : readHead(chunk, offset);
        }
    }

    private int skipBody(int length, int offset) {
        int skipped = (int) Math.min(bodyBytesRemaining, length - offset);
        bodyBytesRemaining -= skipped;
        return offset + skipped;
    }

    private int readHead(byte[] chunk, int offset) {
        head.write(chunk[offset]);
        lastFourBytes = (lastFourBytes << 8) | (chunk[offset] & 0xff);
        if (head.size() == 2 && (lastFourBytes & 0xffff) == LINE_END) {
            head.reset();
        } else if (head.size() >= 4 && lastFourBytes == HEAD_END) {
            completeHead(head.toString(StandardCharsets.ISO_8859_1));
        }
        return offset + 1;
    }

    private void completeHead(String text) {
        head.reset();
        if (text.toLowerCase(Locale.ROOT).contains("\r\ntransfer-encoding:")) {
            throw new IllegalStateException("cannot frame a request with Transfer-Encoding: " + text);
        }
        Matcher length = CONTENT_LENGTH.matcher(text);
        bodyBytesRemaining = length.find() ? Long.parseLong(length.group(1)) : 0;
        onRequest.run();
    }
}
