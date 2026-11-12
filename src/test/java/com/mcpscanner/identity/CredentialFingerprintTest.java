package com.mcpscanner.identity;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialFingerprintTest {

    private final Identity alice = Identity.create("alice", IdentityRole.PEER, IdentityAuthKind.BEARER);

    @Test
    void theSameUserAndTokenGiveTheSameFingerprint() {
        assertThat(fingerprint(alice, bearer("alpha"))).isEqualTo(fingerprint(alice, bearer("alpha")));
    }

    @Test
    void aNewTokenChangesTheFingerprint() {
        assertThat(fingerprint(alice, bearer("alpha"))).isNotEqualTo(fingerprint(alice, bearer("beta")));
    }

    @Test
    void gainingOrLosingACredentialChangesTheFingerprint() {
        assertThat(CredentialFingerprint.of(alice, Optional.empty())).isNotEqualTo(fingerprint(alice, bearer("alpha")));
    }

    @Test
    void anotherAuthKindChangesTheFingerprint() {
        Identity headers = alice.withAuthKind(IdentityAuthKind.CUSTOM_HEADERS);

        assertThat(CredentialFingerprint.of(alice, Optional.empty()))
                .isNotEqualTo(CredentialFingerprint.of(headers, Optional.empty()));
    }

    @Test
    void otherHeaderValuesOrNamesChangeTheFingerprint() {
        Identity headers = alice.withAuthKind(IdentityAuthKind.CUSTOM_HEADERS).withHeaderNames(List.of("X-Key"));
        AuthStrategy one = new CustomHeaderAuthStrategy(Map.of("X-Key", "one"));

        assertThat(fingerprint(headers, one))
                .isNotEqualTo(fingerprint(headers, new CustomHeaderAuthStrategy(Map.of("X-Key", "two"))))
                .isNotEqualTo(fingerprint(headers.withHeaderNames(List.of("X-Other")), one));
    }

    @Test
    void renamingTheUserKeepsTheFingerprint() {
        assertThat(fingerprint(alice.withName("alicia"), bearer("alpha"))).isEqualTo(fingerprint(alice, bearer("alpha")));
    }

    @Test
    void theFingerprintNamesTheUserAndHidesTheSecret() {
        CredentialFingerprint fingerprint = fingerprint(alice, bearer("super-secret-token"));

        assertThat(fingerprint.identityId()).isEqualTo(alice.id());
        assertThat(fingerprint.digest()).doesNotContain("super-secret-token").matches("[0-9a-f]{64}");
        assertThat(fingerprint.toString()).doesNotContain("super-secret-token");
    }

    @Test
    void aSignedInSessionKeepsItsFingerprintWhenItsTokenRenewsButNotWhenReplaced() {
        Identity oauth = alice.withAuthKind(IdentityAuthKind.OAUTH);
        RenewingSession session = new RenewingSession("first");
        CredentialFingerprint signedIn = fingerprint(oauth, session);

        session.renew("second");

        assertThat(fingerprint(oauth, session)).isEqualTo(signedIn);
        assertThat(fingerprint(oauth, new RenewingSession("second"))).isNotEqualTo(signedIn);
    }

    private static CredentialFingerprint fingerprint(Identity user, AuthStrategy credential) {
        return CredentialFingerprint.of(user, Optional.of(credential));
    }

    private static AuthStrategy bearer(String token) {
        return new BearerTokenAuthStrategy(token);
    }

    private static final class RenewingSession implements AuthStrategy {
        private final AtomicReference<String> token;

        RenewingSession(String token) {
            this.token = new AtomicReference<>(token);
        }

        void renew(String replacement) {
            token.set(replacement);
        }

        @Override
        public Map<String, String> headers() {
            return Map.of("Authorization", "Bearer " + token.get());
        }

        @Override
        public boolean supportsRefresh() {
            return true;
        }
    }
}
