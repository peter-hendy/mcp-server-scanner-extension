package com.mcpscanner.core.oauth;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

final class TokenResponseValidator {
    OAuthCredential validate(TokenResponse response, CredentialBinding binding, OAuthCredential previous, Clock clock) {
        if (response == null || response.status() != 200 || response.fields().containsKey("error")) throw invalid();
        var fields = response.fields();
        String access = token(fields.get("access_token"));
        if (!(fields.get("token_type") instanceof String type) || !"Bearer".equalsIgnoreCase(type)) throw invalid();
        String refresh = fields.containsKey("refresh_token") ? token(fields.get("refresh_token")) : previous == null ? null : previous.refreshToken();
        Set<String> granted = scopes(fields.get("scope"), previous == null ? binding.scopes() : previous.grantedScopes());
        return new OAuthCredential(binding, access, refresh, expiresAt(fields.get("expires_in"), clock), granted);
    }

    private String token(Object value) {
        if (!(value instanceof String token) || token.isBlank()
                || token.chars().anyMatch(character -> character < 0x21 || character > 0x7e)) throw invalid();
        return token;
    }

    private Set<String> scopes(Object value, Set<String> requested) {
        if (value == null) return requested;
        if (!(value instanceof String scope)) throw invalid();
        var scopes = new TreeSet<String>();
        for (String granted : scope.split(" ")) {
            if (granted.isEmpty()) continue;
            if (!ScopeValues.valid(granted)) throw invalid();
            scopes.add(granted);
        }
        return scopes;
    }

    private Instant expiresAt(Object value, Clock clock) {
        if (value == null) return null;
        try {
            long seconds = new java.math.BigDecimal(value.toString()).longValueExact();
            if (!(value instanceof Number || value instanceof String) || seconds <= 0) throw invalid();
            return clock.instant().plusSeconds(seconds);
        } catch (ArithmeticException | java.time.DateTimeException | NumberFormatException failure) { throw invalid(); }
    }

    private OAuthFlowException invalid() {
        return new OAuthFlowException(OAuthFlowException.Reason.INVALID_TOKEN_RESPONSE, "Token endpoint returned an invalid response");
    }
}
