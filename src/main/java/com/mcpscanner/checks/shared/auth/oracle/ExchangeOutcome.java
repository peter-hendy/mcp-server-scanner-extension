package com.mcpscanner.checks.shared.auth.oracle;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

record ExchangeOutcome(Kind kind, int status, Optional<JsonNode> result) {

    enum Kind { SUCCESSFUL, REFUSED, INCONCLUSIVE }

    static ExchangeOutcome inconclusive(int status) {
        return new ExchangeOutcome(Kind.INCONCLUSIVE, status, Optional.empty());
    }

    static ExchangeOutcome refused(int status) {
        return new ExchangeOutcome(Kind.REFUSED, status, Optional.empty());
    }

    static ExchangeOutcome successful(int status, Optional<JsonNode> result) {
        return new ExchangeOutcome(Kind.SUCCESSFUL, status, result);
    }

    boolean successful() {
        return kind == Kind.SUCCESSFUL;
    }

    boolean refused() {
        return kind == Kind.REFUSED;
    }
}
