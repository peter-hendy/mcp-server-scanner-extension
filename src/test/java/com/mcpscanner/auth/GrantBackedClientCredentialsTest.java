package com.mcpscanner.auth;

import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.core.oauth.*;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GrantBackedClientCredentialsTest {
    private final URI issuer = URI.create("https://issuer.example");
    private final URI resource = URI.create("https://resource.example/mcp");
    private final Instant now = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void explicitlyConfiguredMachineStrategyFitsExistingConnectionWithoutBrowser() {
        var requests = new ArrayList<TokenRequest>();
        var strategy = GrantBackedAuthStrategy.clientCredentials(resource, config(), request -> {
            requests.add(request);
            return token("machine-1");
        }, Clock.fixed(now, ZoneOffset.UTC));
        var connection = new McpServerConfig(resource.toString(), TransportType.AUTO, strategy);

        assertThat(connection.auth().headers()).containsEntry("Authorization", "Bearer machine-1");
        assertThat(connection.auth().headers()).containsEntry("Authorization", "Bearer machine-1");
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.endpoint()).isEqualTo(URI.create("https://issuer.example/token"));
            assertThat(request.form()).containsEntry("grant_type", "client_credentials")
                    .containsEntry("resource", resource.toString()).doesNotContainKeys("code", "refresh_token");
            assertThat(request.headers()).containsKey("Authorization");
        });
    }

    @Test
    void exposesTheCurrentTokenWithoutFetchingOneAndRenewsByRerunningTheGrant() {
        var requests = new ArrayList<TokenRequest>();
        var strategy = GrantBackedAuthStrategy.clientCredentials(resource, config(), request -> {
            requests.add(request);
            return token("machine-" + requests.size());
        }, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(strategy.currentToken()).isEmpty();
        strategy.headers();
        assertThat(strategy.currentToken()).hasValueSatisfying(token -> {
            assertThat(token.value()).isEqualTo("machine-1");
            assertThat(token.expiresAt()).contains(now.plusSeconds(120));
        });

        assertThat(strategy.renew()).isTrue();

        assertThat(strategy.currentToken().orElseThrow().value()).isEqualTo("machine-2");
        assertThat(requests).hasSize(2);
    }

    @Test
    void connectionRejectsDifferentResourceBeforeInitialAndForcedGrant() {
        for (boolean renew : List.of(false, true)) {
            var requests = new ArrayList<TokenRequest>();
            var strategy = GrantBackedAuthStrategy.clientCredentials(resource, config(), request -> {
                requests.add(request);
                return token("machine-1");
            }, Clock.fixed(now, ZoneOffset.UTC));
            if (renew) {
                new McpServerConfig(resource.toString(), TransportType.AUTO, strategy);
                strategy.refresh();
            }
            int previousRequests = requests.size();
            for (String endpoint : List.of("https://other.example/mcp", "https://resource.example/other",
                    "https://resource.example/mcp-extra", "https://resource.example:444/mcp",
                    "http://resource.example/mcp", "https://resource.example/mcp?other=true",
                    "https://user@resource.example/mcp", "https://resource.example/mcp#fragment")) {
                assertThatThrownBy(() -> new McpServerConfig(endpoint, TransportType.AUTO, strategy))
                        .isInstanceOf(OAuthFlowException.class);
            }
            assertThat(requests).hasSize(previousRequests);
        }
    }

    @Test
    void originAudienceAllowsEndpointPathAndRetainsAudienceDuringForcedRenewal() {
        URI origin = URI.create("https://resource.example");
        var requests = new ArrayList<TokenRequest>();
        var strategy = GrantBackedAuthStrategy.clientCredentials(resource, config(origin), request -> {
            requests.add(request);
            return token("machine-" + requests.size());
        }, Clock.fixed(now, ZoneOffset.UTC));
        var connection = new McpServerConfig(resource.toString(), TransportType.AUTO, strategy);

        assertThat(connection.auth().headers()).containsEntry("Authorization", "Bearer machine-1");
        assertThat(connection.auth().refresh()).isTrue();
        assertThat(connection.auth().headers()).containsEntry("Authorization", "Bearer machine-2");
        assertThatThrownBy(() -> new McpServerConfig("https://other.example/mcp", TransportType.AUTO, strategy))
                .isInstanceOf(OAuthFlowException.class);
        assertThat(requests).hasSize(2).allSatisfy(request -> assertThat(request.form())
                .containsEntry("resource", origin.toString()));
    }

    @Test
    void originAudienceCannotReuseStrategyForAnotherEndpointBeforeOrAfterRenewal() {
        var requests = new ArrayList<TokenRequest>();
        var strategy = GrantBackedAuthStrategy.clientCredentials(resource, config(URI.create("https://resource.example")), request -> {
            requests.add(request);
            return token("machine-" + requests.size());
        }, Clock.fixed(now, ZoneOffset.UTC));
        new McpServerConfig(resource.toString(), TransportType.AUTO, strategy);
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer machine-1");

        for (boolean renew : List.of(false, true)) {
            if (renew) {
                strategy.refresh();
            }
            int previousRequests = requests.size();
            for (String endpoint : List.of("https://resource.example/other", "https://resource.example/mcp/subpath",
                    "https://resource.example/mcp?other=true", "https://other.example/mcp",
                    "http://resource.example/mcp", "https://resource.example:444/mcp",
                    "https://user@resource.example/mcp", "https://resource.example/mcp#fragment")) {
                assertThatThrownBy(() -> new McpServerConfig(endpoint, TransportType.AUTO, strategy))
                        .isInstanceOf(OAuthFlowException.class);
            }
            assertThat(requests).hasSize(previousRequests);
        }
    }

    @Test
    void constructionRejectsAudienceThatDoesNotCoverIntendedEndpointBeforeExchange() {
        for (URI endpoint : List.of(URI.create("https://other.example/mcp"),
                URI.create("https://resource.example/other"), URI.create("https://resource.example/mcp/child"))) {
            assertThatThrownBy(() -> GrantBackedAuthStrategy.clientCredentials(endpoint, config(), request -> {
                throw new AssertionError("Invalid endpoint cannot send a token request");
            }, Clock.fixed(now, ZoneOffset.UTC)))
                    .isInstanceOfSatisfying(OAuthFlowException.class,
                            failure -> assertThat(failure.reason()).isEqualTo(OAuthFlowException.Reason.INVALID_CONFIGURATION));
        }
    }

    @Test
    void expirationRenewsSameMachineGrantAndKeepsConnectionsIsolated() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        var requests = new ArrayList<TokenRequest>();
        TokenExchange exchange = request -> {
            requests.add(request);
            return token("machine-" + requests.size());
        };
        var strategy = GrantBackedAuthStrategy.clientCredentials(resource, config(), exchange, clock);
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer machine-1");
        when(clock.instant()).thenReturn(now.plusSeconds(100));
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer machine-2");
        var otherConnection = GrantBackedAuthStrategy.clientCredentials(resource, config(), exchange, clock);
        assertThat(otherConnection.headers()).containsEntry("Authorization", "Bearer machine-3");
        assertThat(requests).allSatisfy(request -> assertThat(request.form())
                .containsEntry("resource", resource.toString()).containsEntry("grant_type", "client_credentials"));
        assertThat(strategy.supportsRefresh()).isTrue();
        assertThat(strategy.refresh()).isTrue();
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer machine-4");
    }

    @Test
    void rejectsWrongIssuerResourceAndUnsupportedMetadataBeforeAnyExchange() {
        var good = config();
        var wrong = URI.create("https://other.example");
        var invalid = List.of(
                new ClientCredentialsConfig(good.binding(), new ProtectedResourceMetadata(wrong, List.of(issuer), Set.of()), good.serverMetadata(), good.client(), good.authentication()),
                new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), good.serverMetadata(), new RegisteredClient(wrong, "scanner", "client-secret"), good.authentication()),
                new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), new ClientCredentialsMetadata(wrong, good.serverMetadata().tokenEndpoint(), Set.of("client_credentials"), Set.of("client_secret_basic"), Set.of()), good.client(), good.authentication()),
                new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), new ClientCredentialsMetadata(issuer, good.serverMetadata().tokenEndpoint(), Set.of("authorization_code"), Set.of("client_secret_basic"), Set.of()), good.client(), good.authentication()),
                new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), new ClientCredentialsMetadata(issuer, good.serverMetadata().tokenEndpoint(), Set.of("client_credentials"), Set.of("none"), Set.of()), new RegisteredClient(issuer, "scanner", null), ClientCredentialsAuthentication.publicClient()));
        for (var config : invalid) {
            var strategy = GrantBackedAuthStrategy.clientCredentials(resource, config, request -> {
                throw new AssertionError("Invalid machine configuration cannot send a token request");
            }, Clock.fixed(now, ZoneOffset.UTC));
            assertThatThrownBy(strategy::headers).isInstanceOfSatisfying(OAuthFlowException.class,
                    failure -> assertThat(failure.reason()).isEqualTo(OAuthFlowException.Reason.INVALID_CONFIGURATION));
        }
    }

    @Test
    void denialAndTransportErrorsHaveTypedSanitizedFailures() {
        for (TokenExchange exchange : List.<TokenExchange>of(
                request -> new TokenResponse(400, Map.of("error", "invalid_client", "error_description", "server-secret")),
                request -> { throw new IllegalStateException("transport-secret"); })) {
            var strategy = GrantBackedAuthStrategy.clientCredentials(resource, config(), exchange, Clock.fixed(now, ZoneOffset.UTC));
            assertThatThrownBy(strategy::headers).isInstanceOfSatisfying(OAuthFlowException.class, failure -> {
                assertThat(failure.reason()).isIn(OAuthFlowException.Reason.INVALID_TOKEN_RESPONSE, OAuthFlowException.Reason.EXCHANGE_FAILED);
                assertThat(failure).hasNoCause();
                assertThat(failure.toString()).doesNotContain("client-secret", "server-secret", "transport-secret");
            });
            assertThat(strategy.toString()).doesNotContain("client-secret");
        }
    }

    @Test
    void eachStrategyRequiresItsOwnExplicitClientAndConnectionStore() {
        var requests = new ArrayList<TokenRequest>();
        TokenExchange exchange = request -> {
            requests.add(request);
            return token("machine-" + requests.size());
        };
        var first = GrantBackedAuthStrategy.clientCredentials(resource, config(), exchange, Clock.fixed(now, ZoneOffset.UTC));
        var second = GrantBackedAuthStrategy.clientCredentials(resource, config(), exchange, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(first.headers()).containsEntry("Authorization", "Bearer machine-1");
        assertThat(second.headers()).containsEntry("Authorization", "Bearer machine-2");
        assertThat(first.headers()).containsEntry("Authorization", "Bearer machine-1");
    }

    private ClientCredentialsConfig config() {
        return config(resource);
    }

    private ClientCredentialsConfig config(URI resource) {
        var binding = new CredentialBinding(issuer, resource, "scanner", Set.of("tools:read"), OAuthGrantMode.CLIENT_CREDENTIALS);
        return new ClientCredentialsConfig(binding, new ProtectedResourceMetadata(resource, List.of(issuer), binding.scopes()),
                new ClientCredentialsMetadata(issuer, URI.create("https://issuer.example/token"), Set.of("client_credentials"), Set.of("client_secret_basic"), Set.of()),
                new RegisteredClient(issuer, "scanner", "client-secret"), ClientCredentialsAuthentication.basic());
    }

    private TokenResponse token(String value) {
        return new TokenResponse(200, Map.of("access_token", value, "token_type", "Bearer", "expires_in", 120));
    }
}
