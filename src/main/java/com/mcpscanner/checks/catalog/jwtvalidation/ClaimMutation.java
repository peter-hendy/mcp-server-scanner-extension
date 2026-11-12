package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Clock;

enum ClaimMutation {

    WRONG_ISSUER("issuer (iss)") {
        @Override
        void mutate(ObjectNode claims, long now) {
            claims.put("iss", "https://untrusted-issuer.mcp-scanner.invalid");
        }
    },
    WRONG_AUDIENCE("audience (aud)") {
        @Override
        void mutate(ObjectNode claims, long now) {
            claims.put("aud", "https://other-resource.mcp-scanner.invalid/mcp");
        }
    },
    EXPIRED("expiry (exp)") {
        @Override
        void mutate(ObjectNode claims, long now) {
            claims.put("exp", now - ONE_HOUR);
            claims.put("iat", now - 2 * ONE_HOUR);
            if (claims.has("nbf")) {
                claims.put("nbf", now - 2 * ONE_HOUR);
            }
        }
    };

    private static final long ONE_HOUR = 3600;

    private final String unvalidatedClaim;

    ClaimMutation(String unvalidatedClaim) {
        this.unvalidatedClaim = unvalidatedClaim;
    }

    String unvalidatedClaim() {
        return unvalidatedClaim;
    }

    ObjectNode applyTo(ObjectNode claims, Clock clock) {
        ObjectNode copy = claims.deepCopy();
        mutate(copy, clock.instant().getEpochSecond());
        return copy;
    }

    abstract void mutate(ObjectNode claims, long now);
}
