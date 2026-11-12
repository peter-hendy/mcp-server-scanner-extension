package com.mcpscanner.identity.credentials;

import com.mcpscanner.identity.IdentityAuthKind;

import java.util.Objects;

public sealed interface CredentialNeed {

    record ServerRefused(String answer, boolean bearerExpected) implements CredentialNeed {
        public ServerRefused {
            Objects.requireNonNull(answer, "answer");
        }
    }

    record OAuthSignInIncomplete(String problem) implements CredentialNeed {
        public OAuthSignInIncomplete {
            Objects.requireNonNull(problem, "problem");
        }
    }

    record MissingCredential(IdentityAuthKind kind, String problem) implements CredentialNeed {
        public MissingCredential {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(problem, "problem");
        }
    }

    record ToolCallsRefused(int count) implements CredentialNeed {
    }
}
