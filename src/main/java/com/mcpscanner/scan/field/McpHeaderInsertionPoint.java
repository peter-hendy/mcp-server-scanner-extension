package com.mcpscanner.scan.field;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

final class McpHeaderInsertionPoint implements AuditInsertionPoint {

    static final String NAME_PREFIX = "MCP header: ";

    private final HttpRequest baseRequest;
    private final HttpHeader header;

    McpHeaderInsertionPoint(HttpRequest baseRequest, HttpHeader header) {
        this.baseRequest = baseRequest;
        this.header = header;
    }

    @Override
    public String name() {
        return NAME_PREFIX + header.name();
    }

    @Override
    public String baseValue() {
        return header.value();
    }

    @Override
    public AuditInsertionPointType type() {
        return AuditInsertionPointType.HEADER;
    }

    @Override
    public HttpRequest buildHttpRequestWithPayload(ByteArray payload) {
        return baseRequest.withUpdatedHeader(header.name(), payload.toString());
    }

    @Override
    public List<Range> issueHighlights(ByteArray payload) {
        int valueStart = headerValueStart();
        return valueStart < 0 ? List.of() : List.of(Range.range(valueStart, valueStart + payload.length()));
    }

    private int headerValueStart() {
        String raw = new String(baseRequest.toByteArray().getBytes(), StandardCharsets.ISO_8859_1);
        String lowerRaw = raw.toLowerCase(Locale.ROOT);
        int nameStart = lowerRaw.indexOf("\r\n" + header.name().toLowerCase(Locale.ROOT) + ":");
        if (nameStart < 0) {
            return -1;
        }
        int cursor = nameStart + header.name().length() + 3;
        while (cursor < raw.length() && raw.charAt(cursor) == ' ') {
            cursor++;
        }
        return cursor;
    }
}
