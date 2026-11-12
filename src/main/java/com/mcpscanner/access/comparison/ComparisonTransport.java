package com.mcpscanner.access.comparison;

import com.mcpscanner.access.AccessRunStopped;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.crossidentity.OperationReply;
import com.mcpscanner.mcp.TooManyRequests;

final class ComparisonTransport {


    private final Http http;

    ComparisonTransport(Http http) {
        this.http = http;
    }

    Http http() {
        return http;
    }

    OperationReply reply(HttpRequest request) {
        return OperationReply.of(send(request));
    }

    HttpRequestResponse send(HttpRequest request) {
        try {
            HttpRequestResponse exchange = http.sendRequest(request);
            return answered(exchange) ? exchange : null;
        } catch (AccessRunStopped stopped) {
            throw stopped;
        } catch (RuntimeException unreachable) {
            return null;
        }
    }

    private static boolean answered(HttpRequestResponse exchange) {
        return exchange != null && exchange.response() != null && !TooManyRequests.answered(exchange);
    }
}
