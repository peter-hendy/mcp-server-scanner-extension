package com.mcpscanner.checks.shared.collaborator;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.http.TempFileCopies;

import java.util.concurrent.atomic.AtomicInteger;

public final class TaggingCopies implements TempFileCopies {

    public static final String KEPT_HEADER = "X-Test-Kept";

    private final AtomicInteger kept = new AtomicInteger();

    @Override
    public HttpRequestResponse kept(HttpRequestResponse exchange) {
        kept.incrementAndGet();
        return HttpRequestResponse.httpRequestResponse(exchange.request().withAddedHeader(KEPT_HEADER, "1"),
                exchange.response());
    }

    public int keptCount() {
        return kept.get();
    }

    public static boolean wasKept(HttpRequestResponse exchange) {
        return exchange.request().hasHeader(KEPT_HEADER);
    }
}
