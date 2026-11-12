package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import java.util.Optional;

record JudgedRefresh(RefreshExchange exchange, Optional<JudgedToken> token) {

    boolean issued() {
        return exchange.issued();
    }

    Usability usability() {
        return token.map(JudgedToken::usability).orElse(Usability.UNVERIFIED);
    }
}
