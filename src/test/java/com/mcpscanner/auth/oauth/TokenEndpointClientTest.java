package com.mcpscanner.auth.oauth;

import com.mcpscanner.auth.oauth.safety.DefaultSuspiciousDestinationGate;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.mcpscanner.core.oauth.OAuthFlowException;
import com.mcpscanner.core.oauth.TokenRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TokenEndpointClientTest {
    @Test
    void privateTokenEndpointIsDeniedBeforeSendingClientSecret() throws Exception {
        var sender = mock(BurpHttpRequestSender.class);
        var response = new HTTPResponse(200);
        response.setBody("{\"access_token\":\"machine-secret\",\"token_type\":\"Bearer\"}");
        when(sender.send(any())).thenReturn(response);
        var request = new TokenRequest(URI.create("https://127.0.0.1/token"),
                Map.of("grant_type", "client_credentials", "resource", "https://server.example/mcp"),
                Map.of("Authorization", "Basic encoded-secret"));

        assertThatThrownBy(() -> new TokenEndpointClient(sender, DefaultSuspiciousDestinationGate.denyingSuspicious(null)).exchange(request))
                .isInstanceOf(OAuthFlowException.class)
                .hasMessage("Token endpoint destination denied");
        verifyNoInteractions(sender);
    }

    @Test
    void tokenPostUsesBurpSenderWithBoundedTimeoutAndPreservesResponse() throws Exception {
        var sender = mock(BurpHttpRequestSender.class);
        var response = new HTTPResponse(200);
        response.setBody("{\"access_token\":\"machine-secret\",\"token_type\":\"Bearer\"}");
        when(sender.send(any())).thenReturn(response);
        var request = new TokenRequest(URI.create("https://issuer.example/token"),
                Map.of("grant_type", "client_credentials", "resource", "https://server.example/mcp"),
                Map.of("Content-Type", "application/x-www-form-urlencoded", "Authorization", "Basic encoded-secret"));

        var result = new TokenEndpointClient(sender, (url, purpose) -> SuspiciousDestinationGate.Decision.allow()).exchange(request);

        var sent = ArgumentCaptor.forClass(HTTPRequest.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getURL().toString()).isEqualTo(request.endpoint().toString());
        assertThat(sent.getValue().getMethod()).isEqualTo(HTTPRequest.Method.POST);
        assertThat(sent.getValue().getBody()).isEqualTo(request.body());
        assertThat(sent.getValue().getHeaderValue("Authorization")).isEqualTo("Basic encoded-secret");
        assertThat(sent.getValue().getReadTimeout()).isEqualTo(OAuthHttpTimeouts.READ_MILLIS);
        assertThat(result.fields()).containsEntry("access_token", "machine-secret");
    }

    @Test
    void tokenEndpointRedirectFailsClosedWithoutFollowingLocation() throws Exception {
        var sender = mock(BurpHttpRequestSender.class);
        var response = new HTTPResponse(307);
        response.setHeader("Location", "https://127.0.0.1/private");
        response.setBody("{\"access_token\":\"redirect-secret\",\"token_type\":\"Bearer\"}");
        when(sender.send(any())).thenReturn(response);
        var request = new TokenRequest(URI.create("https://issuer.example/token"), Map.of(), Map.of());
        var exchange = new TokenEndpointClient(sender, (url, purpose) -> SuspiciousDestinationGate.Decision.allow());

        assertThatThrownBy(() -> exchange.exchange(request)).isInstanceOf(OAuthFlowException.class)
                .hasMessage("Token endpoint redirect rejected");
        verify(sender).send(any());
        verifyNoMoreInteractions(sender);
    }

    @Test
    void malformedResponseAndNetworkFailureAreTypedAndSanitized() throws Exception {
        var sender = mock(BurpHttpRequestSender.class);
        var response = new HTTPResponse(200);
        response.setBody("server-secret malformed JSON");
        when(sender.send(any())).thenReturn(response).thenThrow(new IllegalStateException("network-secret"));
        var exchange = new TokenEndpointClient(sender, (url, purpose) -> SuspiciousDestinationGate.Decision.allow());
        var request = new TokenRequest(URI.create("https://issuer.example/token"), Map.of(), Map.of());

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> exchange.exchange(request)).isInstanceOfSatisfying(OAuthFlowException.class, failure -> {
                assertThat(failure.reason()).isEqualTo(OAuthFlowException.Reason.EXCHANGE_FAILED);
                assertThat(failure).hasNoCause();
                assertThat(failure.toString()).doesNotContain("server-secret", "network-secret");
            });
        }
    }
}
