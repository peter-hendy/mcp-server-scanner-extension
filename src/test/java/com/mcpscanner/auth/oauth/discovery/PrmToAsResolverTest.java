package com.mcpscanner.auth.oauth.discovery;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.auth.oauth.OAuthUrlValidator;
import com.mcpscanner.auth.oauth.safety.DefaultSuspiciousDestinationGate;
import com.mcpscanner.auth.oauth.safety.HostResolver;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationConfirmer;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.logging.McpEventLog;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrmToAsResolverTest {

    private static final URI MCP_RESOURCE = URI.create("https://mcp.example.com/mcp");
    private static final URI PRM_URL = URI.create("https://mcp.example.com/.well-known/oauth-protected-resource");
    private static final URI AS_ISSUER = URI.create("https://example/issuer");
    private static final URI AS_METADATA_URL = URI.create("https://example/.well-known/oauth-authorization-server/issuer");

    private Http http;
    private PrmToAsResolver resolver;
    private final Map<String, HttpRequestResponse> responsesByUrl = new HashMap<>();

    @BeforeEach
    void setUp() {
        com.mcpscanner.testutil.MontoyaTestFactory.install();
        http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    HttpRequestResponse stubbed = responsesByUrl.get(request.url());
                    return stubbed != null ? stubbed : responseWith(null);
                });
        resolver = new PrmToAsResolver(http, null, allowAllGate());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{}", "{\"resource\":\"https://mcp.example.com/other\"}",
            "{\"resource\":\"https://mcp.example.com/mcp/\"}",
            "{\"resource\":\"https://mcp.example.com/mcp#fragment\"}",
            "{\"resource\":42}"
    })
    void rejectsMissingOrDifferentProtectedResourceBeforeFetchingIssuer(String resourceFields) {
        String prm = resourceFields.substring(0, resourceFields.length() - 1)
                + (resourceFields.length() > 2 ? "," : "")
                + "\"authorization_servers\":[\"" + AS_ISSUER + "\"]}";
        stubResponse(PRM_URL, 200, prm);
        stubResponse(AS_METADATA_URL, 200, validAsMetadata(AS_ISSUER.toString()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.resolve(
                PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE))
                .isInstanceOf(com.mcpscanner.auth.oauth.OAuthException.class)
                .hasMessageContaining("resource");
        verify(http, never()).sendRequest(argThat(matchesUrl(AS_METADATA_URL)), any(RequestOptions.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{}", "{", "[]", "42", "null", "\"metadata\"",
            "{\"resource\":\"https://mcp.example.com/mcp\"} {}"
    })
    void invalidProtectedResourceCannotFallThroughToDirectIssuerDiscovery(String prmBody) {
        stubResponse(PRM_URL, 200, prmBody);
        MetadataProbe directIssuerFallback = mock(MetadataProbe.class);
        DiscoveredMetadata fallbackMetadata = new DiscoveredMetadata(
                AS_ISSUER, DiscoverySource.AS_WELL_KNOWN, mock(AuthorizationServerMetadata.class));
        when(directIssuerFallback.probe(MCP_RESOURCE)).thenReturn(Optional.of(fallbackMetadata));
        OAuthMetadataDiscoverer discoverer = new OAuthMetadataDiscoverer(java.util.List.of(
                resource -> resolver.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, resource),
                directIssuerFallback));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> discoverer.discover(MCP_RESOURCE))
                .isInstanceOf(com.mcpscanner.auth.oauth.OAuthException.class)
                .hasMessageContaining("resource");
        verify(directIssuerFallback, never()).probe(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {200, 404})
    void unavailableProtectedResourceMetadataRetainsDirectIssuerFallback(int status) throws Exception {
        stubResponse(PRM_URL, status, status == 200 ? "" : "{not metadata");
        MetadataProbe directIssuerFallback = mock(MetadataProbe.class);
        DiscoveredMetadata fallbackMetadata = new DiscoveredMetadata(
                AS_ISSUER, DiscoverySource.AS_WELL_KNOWN, mock(AuthorizationServerMetadata.class));
        when(directIssuerFallback.probe(MCP_RESOURCE)).thenReturn(Optional.of(fallbackMetadata));
        OAuthMetadataDiscoverer discoverer = new OAuthMetadataDiscoverer(java.util.List.of(
                resource -> resolver.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, resource),
                directIssuerFallback));

        assertThat(discoverer.discover(MCP_RESOURCE)).isSameAs(fallbackMetadata);
        verify(directIssuerFallback).probe(MCP_RESOURCE);
    }

    @Test
    void resolveCapturesTheProtectedResourceMetadataUrlAndRawDocument() {
        String prm = "{\"resource\":\"" + MCP_RESOURCE + "\",\"authorization_servers\":[\"" + AS_ISSUER + "\"]}";
        stubResponse(PRM_URL, 200, prm);
        stubResponse(AS_METADATA_URL, 200, validAsMetadata(AS_ISSUER.toString()));

        DiscoveredMetadata result = resolver.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE)
                .orElseThrow();

        assertThat(result.protectedResource().url()).isEqualTo(PRM_URL);
        assertThat(result.protectedResource().json()).isEqualTo(prm);
    }

    @Test
    void omitsScopesWhenProtectedResourceDoesNotAdvertiseThem() {
        stubResponse(PRM_URL, 200, "{\"resource\":\"" + MCP_RESOURCE
                + "\",\"authorization_servers\":[\"" + AS_ISSUER + "\"]}");
        String asMetadata = validAsMetadata(AS_ISSUER.toString());
        stubResponse(AS_METADATA_URL, 200, asMetadata.substring(0, asMetadata.length() - 1)
                + ",\"scopes_supported\":[\"admin\"]}");

        assertThat(resolver.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE)
                .orElseThrow().selectedScopes()).isEmpty();
    }

    @Test
    void oversizedProtectedResourceMetadataIsRejectedBeforeStringConversion() {
        stubResponse(PRM_URL, 200, "{\"resource\":\"" + MCP_RESOURCE
                + "\",\"authorization_servers\":[\"" + AS_ISSUER + "\"]}");
        stubResponse(AS_METADATA_URL, 200, validAsMetadata(AS_ISSUER.toString()));
        HttpResponse response = responsesByUrl.get(PRM_URL.toString()).response();
        burp.api.montoya.core.ByteArray body = mock(burp.api.montoya.core.ByteArray.class);
        when(body.length()).thenReturn(com.mcpscanner.mcp.BoundedBodyReader.MAX_RESPONSE_BYTES + 1);
        when(response.body()).thenReturn(body);

        assertThat(resolver.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE)).isEmpty();
        verify(response, never()).bodyToString();
    }

    @Test
    void selectsProtectedResourceScopesInsteadOfAuthorizationServerScopes() {
        stubResponse(PRM_URL, 200, "{\"resource\":\"" + MCP_RESOURCE
                + "\",\"authorization_servers\":[\"" + AS_ISSUER
                + "\"],\"scopes_supported\":[\"files:read\",\"tools:invoke\"]}");
        String asMetadata = validAsMetadata(AS_ISSUER.toString());
        stubResponse(AS_METADATA_URL, 200, asMetadata.substring(0, asMetadata.length() - 1)
                + ",\"scopes_supported\":[\"admin\"]}");

        DiscoveredMetadata metadata = resolver.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN,
                MCP_RESOURCE).orElseThrow();

        assertThat(metadata.selectedScopes()).containsExactly("files:read", "tools:invoke");
    }

    @Test
    void rejectsMismatchedOidcIssuerBeforeAnyFurtherFallback() {
        stubResponse(PRM_URL, 200, "{\"resource\":\"" + MCP_RESOURCE + "\",\"authorization_servers\":[\"" + AS_ISSUER + "\"]}");
        stubResponse(URI.create("https://example/.well-known/openid-configuration/issuer"), 200,
                validAsMetadata("https://other.example"));
        stubResponse(URI.create("https://example/issuer/.well-known/openid-configuration"), 200,
                validAsMetadata(AS_ISSUER.toString()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE))
                .isInstanceOf(com.mcpscanner.auth.oauth.OAuthException.class).hasMessageContaining("issuer mismatch");
        verify(http, never()).sendRequest(argThat(request -> request.url()
                .equals("https://example/issuer/.well-known/openid-configuration")), any(RequestOptions.class));
    }

    @Test
    void redirectedPrmStillMustMatchOriginalMcpResource() {
        URI redirectedPrmUrl = URI.create("https://metadata.example/prm");
        stubRedirect(PRM_URL, 302, redirectedPrmUrl.toString());
        stubResponse(redirectedPrmUrl, 200, "{\"resource\":\"https://mcp.example.com/other\","
                + "\"authorization_servers\":[\"" + AS_ISSUER + "\"]}");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.resolve(
                PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE))
                .isInstanceOf(com.mcpscanner.auth.oauth.OAuthException.class)
                .hasMessageContaining("resource");

        verify(http, never()).sendRequest(argThat(matchesUrl(AS_METADATA_URL)), any(RequestOptions.class));
    }

    @Test
    void redirectedAsMetadataStillMustMatchOriginalIssuer() {
        stubResponse(PRM_URL, 200, "{\"resource\":\"" + MCP_RESOURCE
                + "\",\"authorization_servers\":[\"" + AS_ISSUER + "\"]}");
        URI redirectedAsUrl = URI.create("https://metadata.example/as");
        stubRedirect(AS_METADATA_URL, 302, redirectedAsUrl.toString());
        stubResponse(redirectedAsUrl, 200, validAsMetadata("https://other.example"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.resolve(
                PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE))
                .isInstanceOf(com.mcpscanner.auth.oauth.OAuthException.class)
                .hasMessageContaining("issuer mismatch");
    }

    @Test
    void resolvesOidcOnlyIssuerAfterRfc8414IsAbsent() {
        stubResponse(PRM_URL, 200, "{\"resource\":\"" + MCP_RESOURCE + "\",\"authorization_servers\":[\"" + AS_ISSUER + "\"]}");
        stubResponse(URI.create("https://example/issuer/.well-known/openid-configuration"), 200,
                validAsMetadata(AS_ISSUER.toString()));

        Optional<DiscoveredMetadata> result = resolver.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE);

        assertThat(result).isPresent();
        assertThat(result.get().issuer()).isEqualTo(AS_ISSUER);
        org.mockito.ArgumentCaptor<HttpRequest> requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, org.mockito.Mockito.times(4)).sendRequest(requests.capture(), any(RequestOptions.class));
        assertThat(requests.getAllValues()).extracting(HttpRequest::url).containsExactly(PRM_URL.toString(),
                AS_METADATA_URL.toString(), "https://example/.well-known/openid-configuration/issuer",
                "https://example/issuer/.well-known/openid-configuration");
    }

    @Test
    void resolveReturnsDiscoveredMetadataWhenPrmAndAsBothValid() {
        stubResponse(PRM_URL, 200, "{\"resource\":\"" + MCP_RESOURCE + "\",\"authorization_servers\":[\"" + AS_ISSUER + "\"]}");
        stubResponse(AS_METADATA_URL, 200, validAsMetadata(AS_ISSUER.toString()));

        Optional<DiscoveredMetadata> result = resolver.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE);

        assertThat(result).isPresent();
        assertThat(result.get().issuer()).isEqualTo(AS_ISSUER);
        assertThat(result.get().source()).isEqualTo(DiscoverySource.PRM_WELL_KNOWN);
        assertThat(result.get().asMetadata().getIssuer().getValue()).isEqualTo(AS_ISSUER.toString());
    }

    @Test
    void resolveReturnsEmptyWhenPrmMissingAuthorizationServers() {
        stubResponse(PRM_URL, 200, "{\"resource\":\"" + MCP_RESOURCE + "\"}");

        assertThat(resolver.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE)).isEmpty();
    }

    @Test
    void resolveReturnsEmptyWhenAsMetadataFetchReturns404() {
        stubResponse(PRM_URL, 200, "{\"resource\":\"" + MCP_RESOURCE + "\",\"authorization_servers\":[\"" + AS_ISSUER + "\"]}");
        stubResponse(AS_METADATA_URL, 404, "");

        assertThat(resolver.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE)).isEmpty();
    }

    @Test
    void resolveEmitsWarnLogWhenResponseNull() {
        McpEventLog eventLog = new McpEventLog(null);
        PrmToAsResolver resolverWithLog = new PrmToAsResolver(http, eventLog, allowAllGate());
        // PRM_URL not stubbed -> answer returns null response

        resolverWithLog.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE);

        assertThat(eventLog.snapshot())
                .anyMatch(entry -> entry.level() == McpEventLog.Level.WARN
                        && entry.message().contains("PRM/AS document fetch failed"));
    }

    @Test
    void gateDeniesPrmUrlDoesNotIssueAnyHttpRequest() {
        SuspiciousDestinationGate denyingGate = (url, purpose) -> SuspiciousDestinationGate.Decision.deny(
                new SuspiciousDestinationGate.Reason(url, "10.0.0.1",
                        java.util.List.of(OAuthUrlValidator.CLASSIFICATION_PRIVATE),
                        purpose, "denied"));
        AtomicInteger sendInvocations = countingHttp();
        PrmToAsResolver gated = new PrmToAsResolver(http, null, denyingGate);

        Optional<DiscoveredMetadata> result = gated.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE);

        assertThat(result).isEmpty();
        assertThat(sendInvocations.get()).isZero();
    }

    @Test
    void gateDeniesAsMetadataUrlSkipsAsMetadataFetchButStillFetchesPrm() {
        stubResponse(PRM_URL, 200, "{\"resource\":\"" + MCP_RESOURCE + "\",\"authorization_servers\":[\"" + AS_ISSUER + "\"]}");
        SuspiciousDestinationGate selectiveGate = (url, purpose) -> {
            if (AS_METADATA_URL.equals(url)) {
                return SuspiciousDestinationGate.Decision.deny(
                        new SuspiciousDestinationGate.Reason(url, "10.0.0.1",
                                java.util.List.of(OAuthUrlValidator.CLASSIFICATION_PRIVATE),
                                purpose, "denied"));
            }
            return SuspiciousDestinationGate.Decision.allow();
        };
        PrmToAsResolver gated = new PrmToAsResolver(http, null, selectiveGate);

        Optional<DiscoveredMetadata> result = gated.resolve(PRM_URL, DiscoverySource.PRM_WELL_KNOWN, MCP_RESOURCE);

        assertThat(result).isEmpty();
        verify(http).sendRequest(argThat(matchesUrl(PRM_URL)), any(RequestOptions.class));
        verify(http, never()).sendRequest(argThat(matchesUrl(AS_METADATA_URL)), any(RequestOptions.class));
    }

    private void stubResponse(URI uri, int status, String body) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) status);
        when(response.bodyToString()).thenReturn(body);
        burp.api.montoya.core.ByteArray bytes = burp.api.montoya.core.ByteArray.byteArray(body);
        when(response.body()).thenReturn(bytes);
        responsesByUrl.put(uri.toString(), responseWith(response));
    }

    private void stubRedirect(URI uri, int status, String location) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) status);
        burp.api.montoya.http.message.HttpHeader locationHeader = mock(burp.api.montoya.http.message.HttpHeader.class);
        when(locationHeader.name()).thenReturn("Location");
        when(locationHeader.value()).thenReturn(location);
        when(response.headers()).thenReturn(java.util.List.of(locationHeader));
        responsesByUrl.put(uri.toString(), responseWith(response));
    }

    private AtomicInteger countingHttp() {
        AtomicInteger counter = new AtomicInteger();
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenAnswer(invocation -> {
                    counter.incrementAndGet();
                    HttpResponse response = mock(HttpResponse.class);
                    when(response.statusCode()).thenReturn((short) 200);
                    when(response.bodyToString()).thenReturn("");
                    return responseWith(response);
                });
        return counter;
    }

    private static HttpRequestResponse responseWith(HttpResponse response) {
        HttpRequestResponse rr = mock(HttpRequestResponse.class);
        when(rr.response()).thenReturn(response);
        return rr;
    }

    private static ArgumentMatcher<HttpRequest> matchesUrl(URI uri) {
        return request -> request != null && uri.toString().equals(request.url());
    }

    private static String validAsMetadata(String issuer) {
        return "{"
                + "\"issuer\":\"" + issuer + "\","
                + "\"authorization_endpoint\":\"" + issuer + "/authorize\","
                + "\"token_endpoint\":\"" + issuer + "/token\","
                + "\"response_types_supported\":[\"code\"],"
                + "\"grant_types_supported\":[\"authorization_code\",\"refresh_token\"],"
                + "\"code_challenge_methods_supported\":[\"S256\"]"
                + "}";
    }

    private static SuspiciousDestinationGate allowAllGate() {
        return new DefaultSuspiciousDestinationGate(
                new OAuthUrlValidator(),
                stubResolver(),
                SuspiciousDestinationConfirmer.alwaysAllow(),
                null);
    }

    private static HostResolver stubResolver() {
        return host -> java.util.List.of();
    }
}
