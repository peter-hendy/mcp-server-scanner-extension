package com.mcpscanner.checks.shared.auth.oracle;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public final class ReplyResults {

    private final ExchangeClassifier classifier = new ExchangeClassifier();

    public Optional<JsonNode> successfulResult(HttpRequestResponse exchange) {
        ExchangeOutcome outcome = classifier.classify(exchange);
        return outcome.successful() ? outcome.result() : Optional.empty();
    }

    public boolean refused(HttpRequestResponse exchange) {
        return classifier.classify(exchange).refused();
    }
}
