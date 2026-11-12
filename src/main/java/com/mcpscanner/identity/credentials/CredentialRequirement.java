package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.oauth.OAuthClientHints;

import java.util.List;
import java.util.Objects;

public sealed interface CredentialRequirement {

    enum NoCredentials implements CredentialRequirement { INSTANCE }

    enum TokenRequired implements CredentialRequirement { INSTANCE }

    enum GrantAtConnect implements CredentialRequirement { INSTANCE }

    record HeaderValuesRequired(List<String> headerNames) implements CredentialRequirement {
        public HeaderValuesRequired {
            headerNames = List.copyOf(headerNames);
        }
    }

    record BrowserSignInRequired(OAuthClientHints hints) implements CredentialRequirement {
        public BrowserSignInRequired {
            Objects.requireNonNull(hints, "hints");
        }
    }

    record Unavailable(String reason) implements CredentialRequirement {
        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
