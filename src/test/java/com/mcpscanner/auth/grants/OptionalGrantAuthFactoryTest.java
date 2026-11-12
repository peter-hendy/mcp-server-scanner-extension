package com.mcpscanner.auth.grants;

import com.mcpscanner.auth.GrantBackedAuthStrategy;
import com.mcpscanner.auth.oauth.BurpHttpRequestSender;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ReadOnlyHTTPRequest;
import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OptionalGrantAuthFactoryTest {
    private final URI endpoint = URI.create("https://resource.example/mcp");
    private final BurpHttpRequestSender sender = mock(BurpHttpRequestSender.class);
    private final ArrayList<ReadOnlyHTTPRequest> requests = new ArrayList<>();

    @Test
    void selectedMachineProfileDiscoversMetadataAndSendsBoundClientCredentialsGrant() throws Exception {
        when(sender.send(any())).thenAnswer(invocation -> {
            ReadOnlyHTTPRequest request = invocation.getArgument(0);
            requests.add(request);
            return response(switch (request.getURL().toString()) {
                case "https://resource.example/.well-known/oauth-protected-resource/mcp" ->
                    "{\"resource\":\"https://resource.example/mcp\",\"authorization_servers\":[\"https://issuer.example\"]}";
                case "https://issuer.example/.well-known/oauth-authorization-server" -> machineMetadata();
                case "https://issuer.example/token" -> "{\"access_token\":\"machine-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
                default -> throw new AssertionError(request.getURL());
            });
        });
        var strategy = factory().create(endpoint, machineSettings());

        assertThat(strategy).isInstanceOf(GrantBackedAuthStrategy.class);
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer machine-token");
        assertThat(requests).hasSize(3);
        assertThat(requests.get(2).getMethod()).isEqualTo(HTTPRequest.Method.POST);
        assertThat(requests.get(2).getBody()).contains("grant_type=client_credentials", "resource=https%3A%2F%2Fresource.example%2Fmcp");
        assertThat(requests.get(2).getHeaderMap().get("Authorization")).singleElement().asString().startsWith("Basic ");
        assertThat(requests.subList(0, 2)).allSatisfy(request -> {
            assertThat(request.getMethod()).isEqualTo(HTTPRequest.Method.GET);
            assertThat(request.getHeaderMap().get("Authorization")).isNull();
        });
    }

    @Test
    void selectedEnterpriseProviderExchangesIdentityGrantBeforeBoundTokenRequest() throws Exception {
        var identityRequests = new ArrayList<com.mcpscanner.core.oauth.EnterpriseIdentityRequest>();
        com.mcpscanner.core.oauth.EnterpriseIdentityProvider provider = request -> {
            identityRequests.add(request);
            return new com.mcpscanner.core.oauth.TokenResponse(200, Map.of(
                    "access_token", "identity-assertion", "issued_token_type", "urn:ietf:params:oauth:token-type:id-jag",
                    "token_type", "N_A", "expires_in", 3600));
        };
        when(sender.send(any())).thenAnswer(invocation -> {
            ReadOnlyHTTPRequest request = invocation.getArgument(0);
            requests.add(request);
            return response(switch (request.getURL().toString()) {
                case "https://resource.example/.well-known/oauth-protected-resource/mcp" ->
                    "{\"resource\":\"https://resource.example/mcp\",\"authorization_servers\":[\"https://issuer.example\"]}";
                case "https://issuer.example/.well-known/oauth-authorization-server" ->
                    "{\"issuer\":\"https://issuer.example\",\"token_endpoint\":\"https://issuer.example/token\",\"grant_types_supported\":[\"urn:ietf:params:oauth:grant-type:jwt-bearer\"],\"authorization_grant_profiles_supported\":[\"urn:ietf:params:oauth:grant-profile:id-jag\"],\"token_endpoint_auth_methods_supported\":[\"client_secret_basic\"]}";
                case "https://issuer.example/token" -> "{\"access_token\":\"enterprise-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
                default -> throw new AssertionError(request.getURL());
            });
        });
        var factory = new OptionalGrantAuthFactory(sender,
                (url, purpose) -> SuspiciousDestinationGate.Decision.allow(),
                new EnterpriseIdentityProviderRegistry(Map.of("organization", provider)), Clock.systemUTC());
        var settings = new OptionalGrantSettings(OptionalGrantSettings.Profile.ENTERPRISE_MANAGED, URI.create("https://issuer.example"),
                "scanner", "ephemeral-secret", Set.of(), ClientCredentialsAuthentication.basic(), "organization");

        var strategy = factory.create(endpoint, settings);

        assertThat(strategy).isInstanceOf(GrantBackedAuthStrategy.class);
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer enterprise-token");
        assertThat(identityRequests).singleElement().satisfies(request -> assertThat(request.form())
                .containsEntry("audience", "https://issuer.example")
                .containsEntry("resource", endpoint.toString())
                .containsEntry("requested_token_type", "urn:ietf:params:oauth:token-type:id-jag"));
        assertThat(requests.get(2).getBody()).contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer",
                "assertion=identity-assertion").doesNotContain("client_credentials");
    }

    @Test
    void unspecifiedProfileIsRejectedBeforeAnyMetadataOrTokenRequest() {
        assertThatThrownBy(() -> new OptionalGrantSettings(null, URI.create("https://issuer.example"), "scanner", "ephemeral-secret", Set.of(),
                ClientCredentialsAuthentication.basic(), null))
                .isInstanceOf(com.mcpscanner.core.oauth.OAuthFlowException.class)
                .hasMessageNotContaining("ephemeral-secret");
        verifyNoInteractions(sender);
    }

    @Test
    void challengeCanAdvertiseNonstandardProtectedResourceMetadataLocation() throws Exception {
        when(sender.send(any())).thenAnswer(invocation -> {
            ReadOnlyHTTPRequest request = invocation.getArgument(0);
            requests.add(request);
            String url = request.getURL().toString();
            if (url.contains(".well-known/oauth-protected-resource")) return new HTTPResponse(404);
            if (url.equals(endpoint.toString())) {
                var challenge = new HTTPResponse(401);
                challenge.setHeader("WWW-Authenticate", "Bearer resource_metadata=\"https://resource.example/custom-metadata\"");
                return challenge;
            }
            return response(switch (url) {
                case "https://resource.example/custom-metadata" ->
                    "{\"resource\":\"https://resource.example/mcp\",\"authorization_servers\":[\"https://issuer.example\"]}";
                case "https://issuer.example/.well-known/oauth-authorization-server" -> machineMetadata();
                case "https://issuer.example/token" -> "{\"access_token\":\"machine-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
                default -> throw new AssertionError(url);
            });
        });

        assertThat(factory().create(endpoint, machineSettings()).headers())
                .containsEntry("Authorization", "Bearer machine-token");
        assertThat(requests).anySatisfy(request -> assertThat(request.getURL().toString())
                .isEqualTo("https://resource.example/custom-metadata"));
    }

    @Test
    void unsupportedGrantAuthMethodAndIssuerMismatchFailBeforeAnyTokenRequest() throws Exception {
        for (String metadata : java.util.List.of(
                machineMetadata().replace("client_credentials", "authorization_code"),
                machineMetadata().replace("client_secret_basic", "none"),
                machineMetadata().replace("\"issuer\":\"https://issuer.example\"", "\"issuer\":\"https://wrong.example\""),
                machineMetadata().replace("\"grant_types_supported\":[\"client_credentials\"],", ""),
                machineMetadata().replace("[\"client_credentials\"]", "\"client_credentials\""),
                machineMetadata().replace("https://issuer.example/token", "http://unsafe.example/token"))) {
            requests.clear();
            installMetadata(metadata, "https://resource.example/mcp");

            assertThatThrownBy(() -> factory().create(endpoint, machineSettings()))
                    .isInstanceOf(com.mcpscanner.core.oauth.OAuthFlowException.class)
                    .hasMessageNotContaining("ephemeral-secret");
            assertThat(requests).allSatisfy(request -> assertThat(request.getMethod()).isEqualTo(HTTPRequest.Method.GET));
        }
    }

    @Test
    void mismatchedProtectedResourceFailsBeforeContactingAuthorizationServer() throws Exception {
        installMetadata(machineMetadata(), "https://other.example/mcp");

        assertThatThrownBy(() -> factory().create(endpoint, machineSettings()))
                .isInstanceOf(com.mcpscanner.core.oauth.OAuthFlowException.class);
        assertThat(requests).hasSize(1);
    }

    @Test
    void deniedMetadataDestinationReceivesNoRequest() {
        var factory = new OptionalGrantAuthFactory(sender,
                (url, purpose) -> SuspiciousDestinationGate.Decision.deny(null),
                new EnterpriseIdentityProviderRegistry(Map.of()), Clock.systemUTC());

        assertThatThrownBy(() -> factory.create(endpoint, machineSettings()))
                .isInstanceOf(com.mcpscanner.core.oauth.OAuthFlowException.class);
        verifyNoInteractions(sender);
    }

    @Test
    void deniedTokenDestinationReceivesNoClientSecret() throws Exception {
        installMetadata(machineMetadata(), endpoint.toString());
        var factory = new OptionalGrantAuthFactory(sender,
                (url, purpose) -> url.getPath().equals("/token")
                        ? SuspiciousDestinationGate.Decision.deny(null) : SuspiciousDestinationGate.Decision.allow(),
                new EnterpriseIdentityProviderRegistry(Map.of()), Clock.systemUTC());

        assertThatThrownBy(() -> factory.create(endpoint, machineSettings()))
                .isInstanceOf(com.mcpscanner.core.oauth.OAuthFlowException.class)
                .hasMessageNotContaining("ephemeral-secret");
        assertThat(requests).hasSize(2).allSatisfy(request -> assertThat(request.getMethod()).isEqualTo(HTTPRequest.Method.GET));
    }

    @Test
    void enterpriseWithoutRegisteredProviderFailsBeforeNetwork() {
        var settings = new OptionalGrantSettings(OptionalGrantSettings.Profile.ENTERPRISE_MANAGED, URI.create("https://issuer.example"),
                "scanner", "ephemeral-secret", Set.of(), ClientCredentialsAuthentication.basic(), "unregistered");

        assertThatThrownBy(() -> factory().create(endpoint, settings))
                .isInstanceOf(com.mcpscanner.core.oauth.OAuthFlowException.class)
                .hasMessageContaining("registered enterprise identity provider");
        verifyNoInteractions(sender);
    }

    @Test
    void enterpriseProfileDoesNotTreatJwtBearerGrantAloneAsEnterpriseAdvertisement() throws Exception {
        installMetadata(machineMetadata().replace("client_credentials", "urn:ietf:params:oauth:grant-type:jwt-bearer"), endpoint.toString());
        var provider = mock(com.mcpscanner.core.oauth.EnterpriseIdentityProvider.class);
        var factory = new OptionalGrantAuthFactory(sender, (url, purpose) -> SuspiciousDestinationGate.Decision.allow(),
                new EnterpriseIdentityProviderRegistry(Map.of("organization", provider)), Clock.systemUTC());
        var settings = new OptionalGrantSettings(OptionalGrantSettings.Profile.ENTERPRISE_MANAGED, URI.create("https://issuer.example"),
                "scanner", "ephemeral-secret", Set.of(), ClientCredentialsAuthentication.basic(), "organization");

        assertThatThrownBy(() -> factory.create(endpoint, settings))
                .isInstanceOf(com.mcpscanner.core.oauth.OAuthFlowException.class);
        verifyNoInteractions(provider);
        assertThat(requests).allSatisfy(request -> assertThat(request.getMethod()).isEqualTo(HTTPRequest.Method.GET));
    }

    @Test
    void advertisedPrivateKeyJwtUsesInjectedSignerWithoutBasicCredentials() throws Exception {
        installMetadata(machineMetadata().replace("client_secret_basic", "private_key_jwt")
                .replace("\"grant_types_supported\"", "\"token_endpoint_auth_signing_alg_values_supported\":[\"RS256\"],\"grant_types_supported\""), endpoint.toString());
        var signedInputs = new ArrayList<String>();
        var auth = ClientCredentialsAuthentication.privateKeyJwt("RS256", "scanner-key", (algorithm, input) -> {
            assertThat(algorithm).isEqualTo("RS256");
            signedInputs.add(new String(input, java.nio.charset.StandardCharsets.US_ASCII));
            return new byte[64];
        });
        var settings = new OptionalGrantSettings(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS, URI.create("https://issuer.example"),
                "scanner", null, Set.of(), auth, null);

        assertThat(factory().create(endpoint, settings).headers()).containsEntry("Authorization", "Bearer machine-token");
        assertThat(signedInputs).hasSize(1);
        assertThat(requests.get(2).getBody()).contains("client_assertion=", "client_assertion_type=")
                .doesNotContain("client_secret");
        assertThat(requests.get(2).getHeaderMap()).doesNotContainKey("Authorization");
    }

    @Test
    void attackerResourceCannotRedirectConfiguredClientCredentialsOrEnterpriseIdentityToAnotherIssuer() throws Exception {
        var provider = mock(com.mcpscanner.core.oauth.EnterpriseIdentityProvider.class);
        for (var profile : OptionalGrantSettings.Profile.values()) {
            requests.clear();
            doAnswer(invocation -> {
                ReadOnlyHTTPRequest request = invocation.getArgument(0);
                requests.add(request);
                return response(switch (request.getURL().toString()) {
                    case "https://resource.example/.well-known/oauth-protected-resource/mcp" ->
                            "{\"resource\":\"https://resource.example/mcp\",\"authorization_servers\":[\"https://attacker.example\"]}";
                    case "https://attacker.example/.well-known/oauth-authorization-server" ->
                            machineMetadata().replace("issuer.example", "attacker.example")
                                    .replace("\"grant_types_supported\"", "\"authorization_grant_profiles_supported\":[\"urn:ietf:params:oauth:grant-profile:id-jag\"],\"grant_types_supported\"");
                    case "https://attacker.example/token" ->
                            "{\"access_token\":\"attacker-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
                    default -> throw new AssertionError(request.getURL());
                });
            }).when(sender).send(any());
            var settings = new OptionalGrantSettings(profile, URI.create("https://issuer.example"), "scanner",
                    "ephemeral-secret", Set.of(), ClientCredentialsAuthentication.basic(), "organization");
            var factory = new OptionalGrantAuthFactory(sender, (url, purpose) -> SuspiciousDestinationGate.Decision.allow(),
                    new EnterpriseIdentityProviderRegistry(Map.of("organization", provider)), Clock.systemUTC());

            assertThatThrownBy(() -> factory.create(endpoint, settings))
                    .isInstanceOf(com.mcpscanner.core.oauth.OAuthFlowException.class)
                    .hasMessageContaining("issuer").hasMessageNotContaining("ephemeral-secret");
            assertThat(requests).hasSize(1).allSatisfy(request -> assertThat(request.getMethod()).isEqualTo(HTTPRequest.Method.GET));
            verifyNoInteractions(provider);
        }
    }

    @Test
    void missingOrInvalidExpectedIssuerIsRejectedBeforeDiscovery() {
        for (URI issuer : java.util.Arrays.asList(null, URI.create("http://issuer.example"),
                URI.create("https://issuer.example#fragment"), URI.create("https://issuer.example?query=true"),
                URI.create("https://secret@issuer.example"))) {
            assertThatThrownBy(() -> new OptionalGrantSettings(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS,
                    issuer, "scanner", "ephemeral-secret", Set.of(), ClientCredentialsAuthentication.basic(), null))
                    .isInstanceOf(com.mcpscanner.core.oauth.OAuthFlowException.class)
                    .hasMessageContaining("issuer").hasMessageNotContaining("ephemeral-secret");
        }
        verifyNoInteractions(sender);
    }

    @Test
    void configuredIssuerCanBeSelectedAfterOtherAdvertisedIssuersWithoutContactingThem() throws Exception {
        doAnswer(invocation -> {
            ReadOnlyHTTPRequest request = invocation.getArgument(0);
            requests.add(request);
            return response(switch (request.getURL().toString()) {
                case "https://resource.example/.well-known/oauth-protected-resource/mcp" ->
                        "{\"resource\":\"https://resource.example/mcp\",\"authorization_servers\":[\"https://other.example\",\"https://issuer.example\"]}";
                case "https://issuer.example/.well-known/oauth-authorization-server" -> machineMetadata();
                case "https://issuer.example/token" -> "{\"access_token\":\"machine-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
                default -> throw new AssertionError(request.getURL());
            });
        }).when(sender).send(any());

        assertThat(factory().create(endpoint, machineSettings()).headers()).containsEntry("Authorization", "Bearer machine-token");
        assertThat(requests).hasSize(3).noneSatisfy(request ->
                assertThat(request.getURL().getHost()).isEqualTo("other.example"));
    }

    private void installMetadata(String serverMetadata, String resource) throws Exception {
        doAnswer(invocation -> {
            ReadOnlyHTTPRequest request = invocation.getArgument(0);
            requests.add(request);
            return response(switch (request.getURL().toString()) {
                case "https://resource.example/.well-known/oauth-protected-resource/mcp" ->
                        "{\"resource\":\"" + resource + "\",\"authorization_servers\":[\"https://issuer.example\"]}";
                case "https://issuer.example/.well-known/oauth-authorization-server" -> serverMetadata;
                case "https://issuer.example/token" -> "{\"access_token\":\"machine-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
                default -> throw new AssertionError(request.getURL());
            });
        }).when(sender).send(any());
    }

    private OptionalGrantAuthFactory factory() {
        return new OptionalGrantAuthFactory(sender, (url, purpose) -> SuspiciousDestinationGate.Decision.allow(),
                new EnterpriseIdentityProviderRegistry(Map.of()), Clock.systemUTC());
    }
    private OptionalGrantSettings machineSettings() {
        return new OptionalGrantSettings(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS, URI.create("https://issuer.example"),
                "scanner", "ephemeral-secret", Set.of(), ClientCredentialsAuthentication.basic(), null);
    }
    private String machineMetadata() {
        return "{\"issuer\":\"https://issuer.example\",\"token_endpoint\":\"https://issuer.example/token\",\"grant_types_supported\":[\"client_credentials\"],\"token_endpoint_auth_methods_supported\":[\"client_secret_basic\"]}";
    }
    private HTTPResponse response(String body) {
        var response = new HTTPResponse(200);
        response.setBody(body);
        return response;
    }
}
