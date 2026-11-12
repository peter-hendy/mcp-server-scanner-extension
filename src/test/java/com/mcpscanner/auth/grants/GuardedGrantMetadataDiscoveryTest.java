package com.mcpscanner.auth.grants;

import com.mcpscanner.auth.oauth.BurpHttpRequestSender;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ReadOnlyHTTPRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuardedGrantMetadataDiscoveryTest {
    private static final URI ENDPOINT = URI.create("https://resource.example/mcp");
    private static final URI ISSUER = URI.create("https://issuer.example");
    private static final String PRM = "https://resource.example/.well-known/oauth-protected-resource/mcp";
    private static final String PRM_REDIRECT = "https://metadata.example/prm.json";
    private static final String AS = "https://issuer.example/.well-known/oauth-authorization-server";
    private static final String AS_REDIRECT = "https://metadata.example/as.json";
    private final BurpHttpRequestSender sender = mock(BurpHttpRequestSender.class);
    private final List<URI> requests = new ArrayList<>();

    @Test
    void followsBoundedMetadataRedirectsAndGatesEveryDestination() throws Exception {
        var gated = new ArrayList<URI>();
        when(sender.send(any())).thenAnswer(invocation -> responseFor(invocation.getArgument(0)));
        var discovery = new GuardedGrantMetadataDiscovery(sender, (url, purpose) -> {
            gated.add(url);
            return SuspiciousDestinationGate.Decision.allow();
        });

        var metadata = discovery.discover(ENDPOINT, ISSUER);

        assertThat(metadata.issuer()).isEqualTo(ISSUER);
        assertThat(metadata.tokenEndpoint()).isEqualTo(URI.create("https://issuer.example/token"));
        assertThat(requests).containsExactly(URI.create(PRM), URI.create(PRM_REDIRECT), URI.create(AS), URI.create(AS_REDIRECT));
        assertThat(gated).containsExactlyElementsOf(requests);
        assertThat(requests).allSatisfy(uri -> assertThat(uri).isNotEqualTo(URI.create("https://evil.example/metadata")));
    }

    @Test
    void deniedRedirectDestinationReceivesNoRequest() throws Exception {
        when(sender.send(any())).thenAnswer(invocation -> responseFor(invocation.getArgument(0)));
        var discovery = new GuardedGrantMetadataDiscovery(sender, (url, purpose) -> url.toString().equals(PRM_REDIRECT)
                ? SuspiciousDestinationGate.Decision.deny(null)
                : SuspiciousDestinationGate.Decision.allow());

        assertThatThrownBy(() -> discovery.discover(ENDPOINT, ISSUER))
                .isInstanceOf(com.mcpscanner.core.oauth.OAuthFlowException.class);
        assertThat(requests).containsExactly(URI.create(PRM));
    }

    @Test
    void duplicateLocationHeadersAreRejected() throws Exception {
        when(sender.send(any())).thenAnswer(invocation -> {
            ReadOnlyHTTPRequest request = invocation.getArgument(0);
            requests.add(URI.create(request.getURL().toString()));
            var response = new HTTPResponse(302);
            response.setHeader("Location", new String[]{PRM_REDIRECT, "https://evil.example/metadata"});
            return response;
        });
        var discovery = new GuardedGrantMetadataDiscovery(sender,
                (url, purpose) -> SuspiciousDestinationGate.Decision.allow());

        assertThatThrownBy(() -> discovery.discover(ENDPOINT, ISSUER))
                .isInstanceOf(com.mcpscanner.core.oauth.OAuthFlowException.class);
        assertThat(requests).containsExactly(URI.create(PRM));
    }

    private HTTPResponse responseFor(ReadOnlyHTTPRequest request) {
        URI uri = URI.create(request.getURL().toString());
        requests.add(uri);
        assertThat(request.getMethod()).isEqualTo(HTTPRequest.Method.GET);
        return switch (uri.toString()) {
            case PRM -> redirect(PRM_REDIRECT);
            case PRM_REDIRECT -> ok("{\"resource\":\"https://resource.example/mcp\",\"authorization_servers\":[\"https://issuer.example\"]}");
            case AS -> redirect(AS_REDIRECT);
            case AS_REDIRECT -> ok("{\"issuer\":\"https://issuer.example\",\"token_endpoint\":\"https://issuer.example/token\"}");
            default -> throw new AssertionError(uri);
        };
    }

    private static HTTPResponse redirect(String location) {
        var response = new HTTPResponse(302);
        response.setHeader("Location", location);
        return response;
    }

    private static HTTPResponse ok(String body) {
        var response = new HTTPResponse(200);
        response.setBody(body);
        return response;
    }
}
