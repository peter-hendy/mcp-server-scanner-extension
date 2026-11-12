package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.IdentityAuthKind;

import java.util.Optional;

final class AuthLabels {

    private AuthLabels() {
    }

    static String identityKind(IdentityAuthKind kind) {
        return switch (kind) {
            case DETECT -> "Detect automatically";
            case NONE -> "None";
            case BEARER -> "Bearer token";
            case CUSTOM_HEADERS -> "Custom headers";
            case OAUTH -> "OAuth sign-in";
            case OAUTH_CLIENT_CREDENTIALS -> "OAuth client credentials";
            case OAUTH_ENTERPRISE_MANAGED -> "OAuth enterprise-managed";
        };
    }

    static String connectedKind(IdentityAuthKind kind) {
        return switch (kind) {
            case OAUTH, DETECT -> "OAuth 2.1";
            case OAUTH_CLIENT_CREDENTIALS -> "OAuth 2.1 (Client credentials)";
            case OAUTH_ENTERPRISE_MANAGED -> "OAuth 2.1 (Enterprise-managed)";
            default -> identityKind(kind);
        };
    }

    static final String DETECT_HELP = "Tries without credentials first. Signs in with OAuth if the server "
            + "advertises it. If the server refuses, Connect asks for a token right there.";

    static final String GRANT_HELP = "Gets an access token from the authorization server at Connect, "
            + "with no browser sign-in. Only the primary user can use it.";

    static Optional<String> help(IdentityAuthKind kind, Optional<IdentityAuthKind> detected) {
        if (kind.isOAuthGrant()) {
            return Optional.of(GRANT_HELP);
        }
        if (kind != IdentityAuthKind.DETECT) {
            return Optional.empty();
        }
        return Optional.of(detected.map(outcome -> "Detected: " + connectedKind(outcome) + " on the last Connect.")
                .orElse(DETECT_HELP));
    }

    static String detected(IdentityAuthKind outcome) {
        return (outcome == IdentityAuthKind.OAUTH ? "OAuth" : identityKind(outcome)) + " (detected)";
    }

    static String identityKindShort(IdentityAuthKind kind) {
        return switch (kind) {
            case DETECT -> "Detect";
            case NONE -> "None";
            case BEARER -> "Bearer";
            case CUSTOM_HEADERS -> "Headers";
            case OAUTH -> "OAuth";
            case OAUTH_CLIENT_CREDENTIALS -> "OAuth client credentials";
            case OAUTH_ENTERPRISE_MANAGED -> "OAuth enterprise";
        };
    }
}
