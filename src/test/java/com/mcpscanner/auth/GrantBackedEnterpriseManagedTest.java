package com.mcpscanner.auth;

import com.mcpscanner.auth.oauth.BurpHttpRequestSender;
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

class GrantBackedEnterpriseManagedTest {
    private final URI issuer = URI.create("https://issuer.example");
    private final URI endpoint = URI.create("https://resource.example/mcp");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void explicitEnterpriseConnectionExchangesIdentityForResourceTokenAndReusesCredential() {
        var identities = new ArrayList<EnterpriseIdentityRequest>();
        var tokens = new ArrayList<TokenRequest>();
        var strategy = GrantBackedAuthStrategy.enterpriseManaged(endpoint, config(endpoint), request -> {
            identities.add(request);
            return identityToken();
        }, request -> {
            tokens.add(request);
            return accessToken("enterprise-1");
        }, clock);
        var connection = new McpServerConfig(endpoint.toString(), TransportType.AUTO, strategy);

        assertThat(connection.auth().headers()).containsEntry("Authorization", "Bearer enterprise-1");
        assertThat(connection.auth().headers()).containsEntry("Authorization", "Bearer enterprise-1");
        assertThat(identities).singleElement().satisfies(request -> assertThat(request.form())
                .containsEntry("audience", issuer.toString()).containsEntry("resource", endpoint.toString())
                .containsEntry("requested_token_type", "urn:ietf:params:oauth:token-type:id-jag"));
        assertThat(tokens).singleElement().satisfies(request -> {
            assertThat(request.endpoint()).isEqualTo(URI.create("https://issuer.example/token"));
            assertThat(request.form()).containsEntry("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    .containsEntry("assertion", "identity-secret").doesNotContainKeys("refresh_token", "code");
        });
        assertThat(strategy.contributedHeaderNames()).containsExactly("Authorization");
    }

    @Test
    void exposesTheCurrentTokenAndRenewsByRerunningTheExchange() {
        var tokens = new ArrayList<TokenRequest>();
        var strategy = GrantBackedAuthStrategy.enterpriseManaged(endpoint, config(endpoint), request -> identityToken(),
                request -> {
                    tokens.add(request);
                    return accessToken("enterprise-" + tokens.size());
                }, clock);

        assertThat(strategy.currentToken()).isEmpty();
        assertThat(strategy.renew()).isTrue();

        assertThat(strategy.currentToken().orElseThrow().value()).isEqualTo("enterprise-1");
    }

    @Test
    void missingOrganizationIdentityProviderHasTypedFailureBeforeAnyExchange() {
        assertThatThrownBy(() -> GrantBackedAuthStrategy.enterpriseManaged(endpoint, config(endpoint), null,
                request -> { throw new AssertionError("Missing IdP cannot contact resource AS"); }, clock))
                .isInstanceOfSatisfying(OAuthFlowException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(OAuthFlowException.Reason.MISSING_CREDENTIAL));
    }

    @Test
    void exactEndpointBindingSurvivesOriginAudienceAndForcedRenewal() {
        var hops = new ArrayList<String>();
        var strategy = GrantBackedAuthStrategy.enterpriseManaged(endpoint, config(URI.create("https://resource.example")), request -> {
            hops.add("idp");
            return identityToken();
        }, request -> {
            hops.add("as");
            return accessToken("enterprise-" + hops.size());
        }, clock);
        new McpServerConfig(endpoint.toString(), TransportType.AUTO, strategy);
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer enterprise-2");
        for (boolean renew : List.of(false, true)) {
            if (renew) {
                assertThat(strategy.supportsRefresh()).isTrue();
                assertThat(strategy.refresh()).isTrue();
            }
            int previousHops = hops.size();
            for (String other : List.of("https://resource.example/other", "https://resource.example/mcp/child",
                    "https://resource.example/mcp?other=true", "https://other.example/mcp",
                    "https://resource.example:444/mcp", "http://resource.example/mcp",
                    "https://user@resource.example/mcp", "https://resource.example/mcp#fragment")) {
                assertThatThrownBy(() -> new McpServerConfig(other, TransportType.AUTO, strategy))
                        .isInstanceOfSatisfying(OAuthFlowException.class,
                                failure -> assertThat(failure.reason()).isEqualTo(OAuthFlowException.Reason.INVALID_CONFIGURATION));
            }
            assertThat(hops).hasSize(previousHops);
        }
        assertThat(hops).containsExactly("idp", "as", "idp", "as");
    }

    @Test
    void wrongResourceIssuerClientAndUnadvertisedProfileCannotContactIdentityProvider() {
        var good = config(endpoint);
        URI other = URI.create("https://other.example");
        var invalid = List.of(
                new EnterpriseManagedConfig(good.binding(), new ProtectedResourceMetadata(other, List.of(issuer), Set.of()), good.serverMetadata(), good.client(), good.authentication(), true),
                new EnterpriseManagedConfig(good.binding(), new ProtectedResourceMetadata(endpoint, List.of(other), Set.of()), good.serverMetadata(), good.client(), good.authentication(), true),
                new EnterpriseManagedConfig(good.binding(), good.resourceMetadata(), new EnterpriseAuthorizationMetadata(other, good.serverMetadata().tokenEndpoint(), good.serverMetadata().grantProfiles(), Set.of("none"), Set.of()), good.client(), good.authentication(), true),
                new EnterpriseManagedConfig(good.binding(), good.resourceMetadata(), good.serverMetadata(), new RegisteredClient(other, "scanner", null), good.authentication(), true),
                new EnterpriseManagedConfig(good.binding(), good.resourceMetadata(), new EnterpriseAuthorizationMetadata(issuer, good.serverMetadata().tokenEndpoint(), Set.of(), Set.of("none"), Set.of()), good.client(), good.authentication(), true));
        for (var config : invalid) {
            var strategy = GrantBackedAuthStrategy.enterpriseManaged(endpoint, config,
                    request -> { throw new AssertionError("Invalid config cannot contact IdP"); },
                    request -> { throw new AssertionError("Invalid config cannot contact AS"); }, clock);
            assertThatThrownBy(strategy::headers).isInstanceOfSatisfying(OAuthFlowException.class,
                    failure -> assertThat(failure.reason()).isEqualTo(OAuthFlowException.Reason.INVALID_CONFIGURATION));
        }
        assertThatThrownBy(() -> GrantBackedAuthStrategy.enterpriseManaged(other, good,
                request -> identityToken(), request -> accessToken("unused"), clock))
                .isInstanceOf(OAuthFlowException.class);
    }

    @Test
    void expirationRenewsBothHopsAndCredentialsRemainConnectionLocal() {
        Clock mutableClock = mock(Clock.class);
        when(mutableClock.instant()).thenReturn(clock.instant());
        var hops = new ArrayList<String>();
        EnterpriseIdentityProvider identityProvider = request -> {
            hops.add("idp");
            return identityToken();
        };
        TokenExchange exchange = request -> {
            hops.add("as");
            return accessToken("enterprise-" + hops.size());
        };
        var first = GrantBackedAuthStrategy.enterpriseManaged(endpoint, config(endpoint), identityProvider, exchange, mutableClock);
        assertThat(first.headers()).containsEntry("Authorization", "Bearer enterprise-2");
        when(mutableClock.instant()).thenReturn(clock.instant().plusSeconds(100));
        assertThat(first.headers()).containsEntry("Authorization", "Bearer enterprise-4");
        var second = GrantBackedAuthStrategy.enterpriseManaged(endpoint, config(endpoint), identityProvider, exchange, mutableClock);
        assertThat(second.headers()).containsEntry("Authorization", "Bearer enterprise-6");
        assertThat(first.headers()).containsEntry("Authorization", "Bearer enterprise-4");
        assertThat(hops).containsExactly("idp", "as", "idp", "as", "idp", "as");
    }

    @Test
    void identityPolicyDenialAndProviderFailureAreTypedSanitizedAndStopBeforeResourceExchange() {
        for (EnterpriseIdentityProvider provider : List.<EnterpriseIdentityProvider>of(
                request -> new TokenResponse(400, Map.of("error", "access_denied", "error_description", "policy-secret")),
                request -> { throw new IllegalStateException("identity-secret"); })) {
            var strategy = GrantBackedAuthStrategy.enterpriseManaged(endpoint, config(endpoint), provider,
                    request -> { throw new AssertionError("Denied identity cannot contact resource AS"); }, clock);
            assertThatThrownBy(strategy::headers).isInstanceOfSatisfying(OAuthFlowException.class, failure -> {
                assertThat(failure.reason()).isIn(OAuthFlowException.Reason.AUTHORIZATION_DENIED, OAuthFlowException.Reason.EXCHANGE_FAILED);
                assertThat(failure).hasNoCause();
                assertThat(failure.toString()).doesNotContain("policy-secret", "identity-secret");
            });
        }
    }

    @Test
    void publicAdapterRequiresGuardedBurpExchangeAndRejectsPrivateDestination() {
        var sender = mock(BurpHttpRequestSender.class);
        var good = config(endpoint);
        var privateServer = new EnterpriseAuthorizationMetadata(issuer, URI.create("https://127.0.0.1/token"),
                good.serverMetadata().grantProfiles(), Set.of("none"), Set.of());
        var config = new EnterpriseManagedConfig(good.binding(), good.resourceMetadata(), privateServer,
                good.client(), good.authentication(), true);
        var strategy = GrantBackedAuthStrategy.enterpriseManaged(endpoint, config,
                request -> identityToken(), new com.mcpscanner.auth.oauth.TokenEndpointClient(sender, com.mcpscanner.auth.oauth.safety.DefaultSuspiciousDestinationGate.denyingSuspicious(null)), clock);

        assertThatThrownBy(strategy::headers).isInstanceOfSatisfying(OAuthFlowException.class,
                failure -> assertThat(failure.reason()).isEqualTo(OAuthFlowException.Reason.EXCHANGE_FAILED));
        verifyNoInteractions(sender);
    }

    private EnterpriseManagedConfig config(URI resource) {
        var binding = new CredentialBinding(issuer, resource, "scanner", Set.of("tools:read"), OAuthGrantMode.ENTERPRISE_MANAGED);
        return new EnterpriseManagedConfig(binding, new ProtectedResourceMetadata(resource, List.of(issuer), binding.scopes()),
                new EnterpriseAuthorizationMetadata(issuer, URI.create("https://issuer.example/token"),
                        Set.of("urn:ietf:params:oauth:grant-profile:id-jag"), Set.of("none"), Set.of()),
                new RegisteredClient(issuer, "scanner", null), ClientCredentialsAuthentication.publicClient(), true);
    }

    private TokenResponse identityToken() {
        return new TokenResponse(200, Map.of("access_token", "identity-secret", "token_type", "N_A", "expires_in", 300,
                "issued_token_type", "urn:ietf:params:oauth:token-type:id-jag"));
    }

    private TokenResponse accessToken(String value) {
        return new TokenResponse(200, Map.of("access_token", value, "token_type", "Bearer", "expires_in", 120));
    }
}
