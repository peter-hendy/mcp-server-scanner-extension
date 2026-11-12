package com.mcpscanner.ui.connect;

import com.mcpscanner.config.AuthTypeNames;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.ui.state.ConnectAttempt;
import com.mcpscanner.ui.state.ConnectPhase;
import com.mcpscanner.ui.state.UiAction;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ConnectAttemptFactoryTest {

    private static final String ENDPOINT = "https://mcp.example.com/mcp";

    private final OAuthClientHints hints = mock(OAuthClientHints.class);
    private final OptionalGrantSettings grantSettings = mock(OptionalGrantSettings.class);
    private final AuthStrategy bearer = () -> Map.of("Authorization", "Bearer token");
    private final FakeAuthSource source = new FakeAuthSource();
    private final ConnectAttemptFactory factory = new ConnectAttemptFactory(source);

    @Test
    void blankEndpointIsRejected() {
        assertThat(factory.attemptFor("  ", TransportType.AUTO, AuthTypeNames.NONE))
                .isEqualTo(new UiAction.ConnectRejected(ConnectPhase.CONNECT, "endpoint URL is required"));
    }

    @Test
    void nonHttpEndpointIsRejected() {
        assertThat(factory.attemptFor("ftp://x", TransportType.AUTO, AuthTypeNames.NONE))
                .isEqualTo(new UiAction.ConnectRejected(ConnectPhase.CONNECT, "Endpoint must use http or https"));
    }

    @Test
    void staticAuthSnapshotsTheCurrentStrategyAndTrimsTheEndpoint() {
        source.strategy = bearer;

        assertThat(attempt(" " + ENDPOINT + " ", AuthTypeNames.BEARER)).isEqualTo(ConnectAttempt.withSnapshot(
                ENDPOINT, TransportType.SSE, "mcp.example.com", AuthTypeNames.BEARER, bearer));
    }

    @Test
    void invalidStaticAuthIsRejectedInTheConnectPhase() {
        source.failure = new IllegalArgumentException("Enter the bearer token");

        assertThat(factory.attemptFor(ENDPOINT, TransportType.SSE, AuthTypeNames.BEARER))
                .isEqualTo(new UiAction.ConnectRejected(ConnectPhase.CONNECT, "Enter the bearer token"));
    }

    @Test
    void selectedOptionalGrantSnapshotsItsSettings() {
        source.optionalGrant = true;

        assertThat(attempt(ENDPOINT, AuthTypeNames.OAUTH)).isEqualTo(ConnectAttempt.withOptionalGrant(
                ENDPOINT, TransportType.SSE, "mcp.example.com", AuthTypeNames.OAUTH, grantSettings));
    }

    @Test
    void oauthSnapshotsTheHintsAndTheEndpointAsResource() {
        assertThat(attempt(ENDPOINT, AuthTypeNames.OAUTH)).isEqualTo(ConnectAttempt.withOauth(
                ENDPOINT, TransportType.SSE, "mcp.example.com", AuthTypeNames.OAUTH, hints,
                URI.create(ENDPOINT)));
    }

    @Test
    void oauthWithASignedInSessionConnectsWithItInsteadOfSigningInAgain() {
        source.signedIn = bearer;

        assertThat(attempt(ENDPOINT, AuthTypeNames.OAUTH)).isEqualTo(ConnectAttempt.withSnapshot(
                ENDPOINT, TransportType.SSE, "mcp.example.com", AuthTypeNames.OAUTH, bearer));
    }

    @Test
    void invalidOAuthSettingsAreRejectedInTheOAuthPhase() {
        source.failure = new IllegalArgumentException("bad issuer");

        assertThat(factory.attemptFor(ENDPOINT, TransportType.SSE, AuthTypeNames.OAUTH))
                .isEqualTo(new UiAction.ConnectRejected(ConnectPhase.OAUTH, "bad issuer"));
    }

    @Test
    void autoKeepsInvalidOAuthSettingsAsAnErrorInsteadOfRejecting() {
        source.failure = new IllegalArgumentException("bad issuer");

        assertThat(attempt(ENDPOINT, AuthTypeNames.AUTO)).isEqualTo(ConnectAttempt.withAuto(
                ENDPOINT, TransportType.SSE, "mcp.example.com", AuthTypeNames.AUTO, null,
                URI.create(ENDPOINT), "bad issuer"));
    }

    @Test
    void autoWithValidOAuthSettingsCarriesTheHints() {
        assertThat(attempt(ENDPOINT, AuthTypeNames.AUTO)).isEqualTo(ConnectAttempt.withAuto(
                ENDPOINT, TransportType.SSE, "mcp.example.com", AuthTypeNames.AUTO, hints,
                URI.create(ENDPOINT), null));
    }

    @Test
    void everyAttemptIsBuiltForTheUserItConnectsAs() {
        source.user = "alice-id";
        source.strategy = bearer;

        assertThat(attempt(ENDPOINT, AuthTypeNames.BEARER).identityId()).isEqualTo("alice-id");
        assertThat(attempt(ENDPOINT, AuthTypeNames.OAUTH).identityId()).isEqualTo("alice-id");
        assertThat(attempt(ENDPOINT, AuthTypeNames.AUTO).identityId()).isEqualTo("alice-id");
        source.signedIn = bearer;
        assertThat(attempt(ENDPOINT, AuthTypeNames.OAUTH).identityId()).isEqualTo("alice-id");
    }

    private ConnectAttempt attempt(String endpoint, String authType) {
        UiAction action = factory.attemptFor(endpoint, TransportType.SSE, authType);
        assertThat(action).isInstanceOf(UiAction.ConnectRequested.class);
        return ((UiAction.ConnectRequested) action).attempt();
    }

    private final class FakeAuthSource implements ConnectAuthSource {
        private boolean optionalGrant;
        private AuthStrategy strategy;
        private IllegalArgumentException failure;
        private AuthStrategy signedIn;
        private String user;

        @Override
        public String connectingUserId() {
            return user;
        }

        @Override
        public Optional<AuthStrategy> signedInSession() {
            return Optional.ofNullable(signedIn);
        }

        @Override
        public boolean isOptionalGrantSelected() {
            return optionalGrant;
        }

        @Override
        public OAuthClientHints oauthHints() {
            return orFail(hints);
        }

        @Override
        public OptionalGrantSettings optionalGrantSettings() {
            return orFail(grantSettings);
        }

        @Override
        public AuthStrategy currentAuthStrategy() {
            return orFail(strategy);
        }

        private <T> T orFail(T value) {
            if (failure != null) {
                throw failure;
            }
            return value;
        }
    }
}
