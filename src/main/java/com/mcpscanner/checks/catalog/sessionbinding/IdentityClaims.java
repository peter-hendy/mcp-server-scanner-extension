package com.mcpscanner.checks.catalog.sessionbinding;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.shared.host.Claims;
import com.mcpscanner.mcp.HttpOrigin;

import java.util.List;

final class IdentityClaims {

    private final Claims<IdentityOnHost> claims = new Claims<>();

    List<Principal> claim(HttpRequest seed, List<Principal> compared) {
        return compared.stream().filter(principal -> claims.tryClaim(IdentityOnHost.of(seed, principal))).toList();
    }

    void settle(HttpRequest seed, IdentityOutcome outcome) {
        IdentityOnHost key = IdentityOnHost.of(seed, outcome.principal());
        switch (outcome.verdict()) {
            case CONCLUSIVE -> { }
            case INCONCLUSIVE -> claims.releaseProbed(key);
            case UNSUITABLE_SEED -> claims.releaseUnprobed(key);
        }
    }

    void releaseUnprobed(HttpRequest seed, List<Principal> claimed) {
        claimed.forEach(principal -> claims.releaseUnprobed(IdentityOnHost.of(seed, principal)));
    }

    void clear() {
        claims.clear();
    }

    private record IdentityOnHost(HttpOrigin origin, String principalId) {

        static IdentityOnHost of(HttpRequest seed, Principal principal) {
            return new IdentityOnHost(HttpOrigin.of(seed.httpService()), principal.id());
        }
    }
}
