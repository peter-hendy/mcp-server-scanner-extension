package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import com.mcpscanner.mcp.TooManyRequests;
import java.util.Optional;

public final class ProbeChannel {

    public enum Exhaustion { RATE_LIMITED, BUDGET_SPENT }

    private final Http http;
    private final SendBudget budget;
    private boolean budgetRefusedAProbe;

    public ProbeChannel(Http http, int budget) {
        this.http = http;
        this.budget = new SendBudget(budget);
    }

    public HttpRequestResponse send(HttpRequest request) {
        HttpRequestResponse exchange = http.sendRequest(request);
        record(exchange);
        return exchange;
    }

    public void record(HttpRequestResponse sentElsewhere) {
        budget.record(TooManyRequests.answered(sentElsewhere));
    }

    public boolean exhausted() {
        budgetRefusedAProbe |= budget.exhausted() && !budget.rateLimited();
        return budget.exhausted();
    }

    public Optional<Exhaustion> exhaustion() {
        if (budget.rateLimited()) {
            return Optional.of(Exhaustion.RATE_LIMITED);
        }
        return budgetRefusedAProbe ? Optional.of(Exhaustion.BUDGET_SPENT) : Optional.empty();
    }

    public int budget() {
        return budget.limit();
    }

}
