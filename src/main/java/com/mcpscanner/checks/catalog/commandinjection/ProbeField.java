package com.mcpscanner.checks.catalog.commandinjection;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import com.mcpscanner.checks.api.SkipSubject;
import com.mcpscanner.checks.shared.evidence.PayloadHighlights;

import com.mcpscanner.mcp.TooManyRequests;
import java.util.Arrays;
import java.util.List;

final class ProbeField {

    static final String MATCHED_CONTROL_SUFFIX = "mcp-control";
    static final int UNANSWERED_PROBES_BEFORE_GIVING_UP = 2;

    private final HttpRequestResponse base;
    private final AuditInsertionPoint insertionPoint;
    private final Http http;
    private final FieldSubject subject;
    private int consecutiveUnanswered;
    private boolean rateLimited;

    ProbeField(HttpRequestResponse base, AuditInsertionPoint insertionPoint, String fieldName, Http http) {
        this.base = base;
        this.insertionPoint = insertionPoint;
        this.http = http;
        this.subject = FieldSubject.of(base.request(), fieldName);
    }

    FieldSubject subject() {
        return subject;
    }

    String described() {
        return SkipSubject.field(base, insertionPoint);
    }

    String original() {
        String value = insertionPoint.baseValue();
        return value == null ? "" : value;
    }

    List<ShellBreakout> breakouts() {
        return Arrays.stream(ShellBreakout.values())
                .filter(breakout -> insertionPoint.type() != AuditInsertionPointType.HEADER || breakout.fitsOnOneLine())
                .toList();
    }

    InjectionReply baseline() {
        return InjectionReply.of(base.response() != null ? base : http.sendRequest(base.request()));
    }

    InjectionReply send(String payload) {
        if (gaveUp()) {
            return InjectionReply.of(null);
        }
        HttpRequestResponse exchange = http.sendRequest(
                insertionPoint.buildHttpRequestWithPayload(ByteArray.byteArray(payload)));
        consecutiveUnanswered = exchange == null || exchange.response() == null ? consecutiveUnanswered + 1 : 0;
        rateLimited |= TooManyRequests.answered(exchange);
        return InjectionReply.of(PayloadHighlights.ofPayload(exchange, insertionPoint, payload));
    }

    boolean gaveUp() {
        return consecutiveUnanswered >= UNANSWERED_PROBES_BEFORE_GIVING_UP;
    }

    boolean unresponsive() {
        return !rateLimited && gaveUp();
    }

    InjectionReply sendMatchedControl() {
        return send(original() + MATCHED_CONTROL_SUFFIX);
    }
}
