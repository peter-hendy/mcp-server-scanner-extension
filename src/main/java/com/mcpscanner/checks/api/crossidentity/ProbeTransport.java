package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.mcp.TooManyRequests;

public final class ProbeTransport {

    public static final int DIRECT_REQUEST_BUDGET = 40;
    private static final SkipReason BUDGET_USED_UP =
            SkipReason.budgetExhausted(DIRECT_REQUEST_BUDGET + " direct requests per identity");
    private static final SkipReason NO_RESPONSE = SkipReason.transportFailure("a probe request got no response");

    private final Http http;
    private final DecisionLog log;
    private final TransportFailure failure;
    private int sent;
    private boolean exhausted;

    public ProbeTransport(Http http, DecisionLog log) {
        this(http, log, new TransportFailure());
    }

    private ProbeTransport(Http http, DecisionLog log, TransportFailure failure) {
        this.http = http;
        this.log = log;
        this.failure = failure;
    }

    public ProbeTransport withFreshBudget(DecisionLog principalLog) {
        return new ProbeTransport(http, principalLog, failure);
    }

    public boolean stopped() {
        return exhausted || failure.raised();
    }

    public HttpRequestResponse send(HttpRequest request) {
        if (stopped()) {
            return null;
        }
        if (sent >= DIRECT_REQUEST_BUDGET) {
            exhausted = true;
            log.notRun(BUDGET_USED_UP);
            return null;
        }
        sent++;
        return deliver(request);
    }

    public HttpRequestResponse sample(HttpRequest request) {
        return stopped() ? null : deliver(request);
    }

    private HttpRequestResponse deliver(HttpRequest request) {
        try {
            HttpRequestResponse exchange = http.sendRequest(request);
            if (exchange == null || exchange.response() == null) {
                return fail(NO_RESPONSE);
            }
            return TooManyRequests.answered(exchange) ? fail(SkipReason.rateLimited()) : exchange;
        } catch (RuntimeException unreachable) {
            return fail(NO_RESPONSE);
        }
    }

    private HttpRequestResponse fail(SkipReason reason) {
        if (!failure.raised()) {
            log.notRun(reason);
        }
        failure.raise();
        return null;
    }
}
