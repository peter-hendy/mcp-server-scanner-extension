package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.http.BurpMessages;
import com.mcpscanner.mcp.ServerPingAnswerer;
import com.mcpscanner.mcp.StreamingResponseReader;
import com.mcpscanner.mcp.UnstreamedReply;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class ProbeReplyReader {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String DEFAULT_CONTENT_TYPE = "application/json";
    private static final Set<String> REPLACED_RESPONSE_HEADERS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        REPLACED_RESPONSE_HEADERS.addAll(List.of(CONTENT_TYPE, "Content-Length", "Transfer-Encoding"));
    }

    private final StreamingResponseReader reader;

    ProbeReplyReader(Duration responseTimeout) {
        this.reader = new StreamingResponseReader(responseTimeout);
    }

    HttpResponse read(int status, Map<String, List<String>> headers, InputStream body, HttpRequest request)
            throws IOException {
        UnstreamedReply reply = reader.read(status, contentType(headers), body, request.bodyToString(),
                ServerPingAnswerer.ignoring());
        return BurpMessages.toResponse(reply.statusCode(), keptHeaders(headers, reply), reply.body());
    }

    private static String contentType(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .filter(header -> CONTENT_TYPE.equalsIgnoreCase(header.getKey()) && !header.getValue().isEmpty())
                .map(header -> header.getValue().get(0))
                .findFirst()
                .orElse(DEFAULT_CONTENT_TYPE);
    }

    private static Map<String, List<String>> keptHeaders(Map<String, List<String>> received, UnstreamedReply reply) {
        Map<String, List<String>> kept = new LinkedHashMap<>();
        received.forEach((name, values) -> {
            if (!REPLACED_RESPONSE_HEADERS.contains(name) && !name.startsWith(":")) {
                kept.put(name, values);
            }
        });
        kept.put(CONTENT_TYPE, List.of(reply.contentType()));
        return kept;
    }
}
