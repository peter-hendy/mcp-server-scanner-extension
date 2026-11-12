package com.mcpscanner.auth.oauth;

import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ReadOnlyHTTPRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BurpMetadataClientTest {

    private static final URI START = URI.create("https://mcp.example/.well-known/oauth-protected-resource");
    private final BurpHttpRequestSender sender = mock(BurpHttpRequestSender.class);
    private final List<ReadOnlyHTTPRequest> sent = new ArrayList<>();

    @Test
    void sendsABoundedGetAndReturnsTheDocument() throws Exception {
        answer(200, null, "{\"resource\":\"x\"}");

        var response = new BurpMetadataClient(sender).fetch(START, destination -> true);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"resource\":\"x\"}");
        assertThat(sent).singleElement().satisfies(request -> {
            assertThat(request.getMethod()).isEqualTo(HTTPRequest.Method.GET);
            assertThat(request.getReadTimeout()).isEqualTo(OAuthHttpTimeouts.READ_MILLIS);
            assertThat(request.getConnectTimeout()).isEqualTo(OAuthHttpTimeouts.CONNECT_MILLIS);
        });
    }

    @Test
    void redirectBodyIsDiscardedSoOnlyTheFinalDocumentIsParsed() throws Exception {
        answer(302, "https://mcp.example/elsewhere", "redirect page");

        var response = new BurpMetadataClient(sender).get(START);

        assertThat(response.location()).isEqualTo("https://mcp.example/elsewhere");
        assertThat(response.body()).isNull();
    }

    @Test
    void ambiguousRedirectIsRejected() throws Exception {
        when(sender.send(any())).thenAnswer(invocation -> {
            HTTPResponse response = new HTTPResponse(302);
            response.setHeader("Location", "https://one.example", "https://two.example");
            return response;
        });

        assertThatThrownBy(() -> new BurpMetadataClient(sender).get(START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transportFailureYieldsNoResponse() throws Exception {
        when(sender.send(any())).thenThrow(new IOException("connection reset"));

        var response = new BurpMetadataClient(sender).get(START);

        assertThat(response.status()).isZero();
        assertThat(response.body()).isNull();
    }

    @Test
    void deniedRedirectTargetIsNeverRequested() throws Exception {
        answer(302, "https://evil.example/metadata", null);

        assertThatThrownBy(() -> new BurpMetadataClient(sender).fetch(START, destination -> destination.equals(START)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(sent).extracting(request -> request.getURL().toString()).containsExactly(START.toString());
    }

    private void answer(int status, String location, String body) throws IOException {
        when(sender.send(any())).thenAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            HTTPResponse response = new HTTPResponse(status);
            if (location != null) {
                response.setHeader("Location", location);
            }
            response.setBody(body);
            return response;
        });
    }
}
