package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.message.HttpRequestResponse;

import com.mcpscanner.mcp.TooManyRequests;
import java.util.Optional;

record ClientReply(String marker, int status, String body, Optional<HttpRequestResponse> exchange) {

    private static final int NO_STATUS = 0;
    static ClientReply unanswered(String marker) {
        return unanswered(marker, Optional.empty());
    }

    static ClientReply unanswered(String marker, Optional<HttpRequestResponse> exchange) {
        return new ClientReply(marker, NO_STATUS, "", exchange);
    }

    boolean answered() {
        return status >= 200 && status < 300;
    }

    boolean rateLimited() {
        return status == TooManyRequests.STATUS;
    }

    boolean carries(String someMarker) {
        return body.contains(someMarker);
    }

    boolean echoesOwnMarker() {
        return carries(marker);
    }
}
