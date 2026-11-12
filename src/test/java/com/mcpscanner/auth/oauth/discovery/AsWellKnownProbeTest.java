package com.mcpscanner.auth.oauth.discovery;

import com.mcpscanner.auth.oauth.safety.DefaultSuspiciousDestinationGate;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.auth.oauth.OAuthException;
import com.mcpscanner.mcp.BoundedBodyReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AsWellKnownProbeTest {

    private static final String ISSUER = "https://issuer.example.com";
    private static final URI MCP_RESOURCE = URI.create("https://issuer.example.com/sse");
    private static final String EXPECTED_AS_URL =
            "https://issuer.example.com/.well-known/oauth-authorization-server";

    private Http http;
    private AsWellKnownProbe probe;

    @BeforeEach
    void setUp() {
        com.mcpscanner.testutil.MontoyaTestFactory.install();
        http = mock(Http.class);
        probe = new AsWellKnownProbe(http, DefaultSuspiciousDestinationGate.allowingSuspicious(null), null);
    }

    @Test
    void oversizedMetadataIsRejectedBeforeParsing() {
        HttpResponse response = mock(HttpResponse.class);
        ByteArray body = mock(ByteArray.class);
        when(body.length()).thenReturn(BoundedBodyReader.MAX_RESPONSE_BYTES + 1);
        when(response.body()).thenReturn(body);
        when(response.statusCode()).thenReturn((short) 200);
        when(response.bodyToString()).thenReturn(validAsMetadata(ISSUER));
        stubHttpReturns(responseWith(response));

        assertThat(probe.probe(MCP_RESOURCE)).isEmpty();
        verify(response, org.mockito.Mockito.never()).bodyToString();
    }

    @Test
    void probeFallsBackToOidcWhenRfc8414IsAbsent() {
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            HttpResponse response = mock(HttpResponse.class);
            boolean oidc = request.url().endsWith("/.well-known/openid-configuration");
            when(response.statusCode()).thenReturn((short) (oidc ? 200 : 404));
            ByteArray body = ByteArray.byteArray(oidc ? validAsMetadata(ISSUER) : "");
            org.mockito.Mockito.lenient().when(response.body()).thenReturn(body);
            org.mockito.Mockito.lenient().when(response.bodyToString()).thenReturn(oidc ? validAsMetadata(ISSUER) : "");
            return responseWith(response);
        });

        assertThat(probe.probe(MCP_RESOURCE)).isPresent();
    }

    @Test
    void probeAdoptsExactMetadataIssuerWithRootSlash() {
        String metadataIssuer = ISSUER + "/";
        stubResponse(200, validAsMetadata(metadataIssuer));

        Optional<DiscoveredMetadata> result = probe.probe(MCP_RESOURCE);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().issuer()).isEqualTo(URI.create(metadataIssuer));
        assertThat(result.orElseThrow().asMetadata().getIssuer().getValue()).isEqualTo(metadataIssuer);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://issuer.example.com/tenant",
            "https://other.example.com",
            "https://issuer.example.com:8443/",
            "http://issuer.example.com/",
            "https://issuer.example.com//"
    })
    void probeRejectsMetadataIssuerOutsideEquivalentRootForms(String metadataIssuer) {
        stubResponse(200, validAsMetadata(metadataIssuer));

        assertThatThrownBy(() -> probe.probe(MCP_RESOURCE))
                .isInstanceOf(OAuthException.class)
                .hasMessage("OAuth metadata issuer mismatch");
    }

    @Test
    void probeReturnsDiscoveredMetadataOnValidResponse() {
        stubResponse(200, validAsMetadata(ISSUER));

        Optional<DiscoveredMetadata> result = probe.probe(MCP_RESOURCE);

        assertThat(result).isPresent();
        assertThat(result.get().issuer()).isEqualTo(URI.create(ISSUER));
        assertThat(result.get().source()).isEqualTo(DiscoverySource.AS_WELL_KNOWN);
        assertThat(result.get().asMetadata().getIssuer().getValue()).isEqualTo(ISSUER);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendRequest(captor.capture(), any(RequestOptions.class));
        assertThat(captor.getValue().url()).isEqualTo(EXPECTED_AS_URL);
        assertThat(captor.getValue().method()).isEqualTo("GET");
    }

    @Test
    void probeReturnsEmptyOn404() {
        stubResponse(404, "");

        assertThat(probe.probe(MCP_RESOURCE)).isEmpty();
    }

    @Test
    void probeReturnsEmptyOnNullResponse() {
        stubNullResponse();

        assertThat(probe.probe(MCP_RESOURCE)).isEmpty();
    }

    @Test
    void probeReturnsEmptyOnMalformedJson() {
        stubResponse(200, "{not valid json");

        assertThat(probe.probe(MCP_RESOURCE)).isEmpty();
    }

    @Test
    void gateDenialSendsNoRequest() {
        AsWellKnownProbe gated = new AsWellKnownProbe(http,
                (url, purpose) -> com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate.Decision.deny(
                        new com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate.Reason(
                                url, "10.0.0.1", java.util.List.of("private"), purpose, "denied")),
                null);

        assertThat(gated.probe(MCP_RESOURCE)).isEmpty();
        verifyNoInteractions(http);
    }

    @Test
    void productionDefaultGateBlocksRedirectToLoopbackBeforeRequest() {
        HttpResponse redirect = mock(HttpResponse.class);
        when(redirect.statusCode()).thenReturn((short) 302);
        HttpHeader location = mock(HttpHeader.class);
        when(location.name()).thenReturn("Location");
        when(location.value()).thenReturn("https://127.0.0.1/private");
        when(redirect.headers()).thenReturn(java.util.List.of(location));
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenAnswer(invocation -> responseWith(redirect));
        AsWellKnownProbe defaultProbe = new AsWellKnownProbe(http, DefaultSuspiciousDestinationGate.allowingSuspicious(null), null);

        assertThat(defaultProbe.probe(URI.create("https://public.example/mcp"))).isEmpty();

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, org.mockito.Mockito.atLeastOnce()).sendRequest(requests.capture(), any(RequestOptions.class));
        assertThat(requests.getAllValues()).extracting(HttpRequest::url).doesNotContain("https://127.0.0.1/private");
    }

    @Test
    void probeEmitsWarnLogWhenResponseNull() {
        McpEventLog eventLog = new McpEventLog(null);
        AsWellKnownProbe probeWithLog = new AsWellKnownProbe(http, DefaultSuspiciousDestinationGate.allowingSuspicious(null), eventLog);
        stubNullResponse();

        probeWithLog.probe(MCP_RESOURCE);

        assertThat(eventLog.snapshot())
                .anyMatch(entry -> entry.level() == McpEventLog.Level.WARN
                        && entry.message().contains("AS well-known fetch failed"));
    }

    private void stubResponse(int status, String body) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) status);
        ByteArray bodyBytes = ByteArray.byteArray(body);
        org.mockito.Mockito.lenient().when(response.body()).thenReturn(bodyBytes);
        when(response.bodyToString()).thenReturn(body);
        stubHttpReturns(responseWith(response));
    }

    private void stubNullResponse() {
        stubHttpReturns(responseWith(null));
    }

    private void stubHttpReturns(HttpRequestResponse rr) {
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class))).thenReturn(rr);
    }

    private static HttpRequestResponse responseWith(HttpResponse response) {
        HttpRequestResponse rr = mock(HttpRequestResponse.class);
        when(rr.response()).thenReturn(response);
        return rr;
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
}
