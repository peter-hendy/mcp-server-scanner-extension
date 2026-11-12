package com.mcpscanner.auth.oauth.discovery;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.auth.oauth.safety.FetchPurpose;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataDocumentFetcherTest {
    private static final FetchPurpose PURPOSE = FetchPurpose.of("metadata", URI.create("https://mcp.example/mcp"));

    private Http http;

    @BeforeEach
    void setUp() {
        com.mcpscanner.testutil.MontoyaTestFactory.install();
        http = mock(Http.class);
    }

    @Test
    void followsRelativeAndCrossHostRedirectsOnlyAfterGatingEachGet() {
        Map<String, StubResponse> responses = Map.of(
                "https://mcp.example/.well-known/prm", new StubResponse(302, "/metadata", null),
                "https://mcp.example/metadata", new StubResponse(307, "https://metadata.example/prm", null),
                "https://metadata.example/prm", new StubResponse(200, null, "metadata"));
        stubResponses(responses);
        List<URI> gated = new ArrayList<>();
        SuspiciousDestinationGate gate = (uri, purpose) -> {
            assertThat(purpose).isSameAs(PURPOSE);
            gated.add(uri);
            return SuspiciousDestinationGate.Decision.allow();
        };

        var body = new MetadataDocumentFetcher(http, gate, McpEventLog.noop())
                .fetch(URI.create("https://mcp.example/.well-known/prm"), PURPOSE);

        assertThat(body).contains("metadata");
        assertThat(gated).containsExactly(
                URI.create("https://mcp.example/.well-known/prm"),
                URI.create("https://mcp.example/metadata"),
                URI.create("https://metadata.example/prm"));
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(http, org.mockito.Mockito.times(3))
                .sendRequest(requests.capture(), any(RequestOptions.class));
        assertThat(requests.getAllValues()).extracting(HttpRequest::url).containsExactly(
                "https://mcp.example/.well-known/prm", "https://mcp.example/metadata",
                "https://metadata.example/prm");
    }

    @Test
    void blocksUnsafeRedirectDestinationBeforeRequestingIt() {
        stubResponses(Map.of("https://mcp.example/metadata",
                new StubResponse(302, "https://private.example/private", null)));
        List<URI> gated = new ArrayList<>();
        SuspiciousDestinationGate gate = (uri, purpose) -> {
            gated.add(uri);
            return uri.getHost().equals("mcp.example")
                    ? SuspiciousDestinationGate.Decision.allow()
                    : SuspiciousDestinationGate.Decision.deny(new SuspiciousDestinationGate.Reason(
                            uri, null, List.of(), purpose, "denied"));
        };

        assertThat(new MetadataDocumentFetcher(http, gate, McpEventLog.noop())
                .fetch(URI.create("https://mcp.example/metadata"), PURPOSE)).isEmpty();

        assertThat(gated).containsExactly(URI.create("https://mcp.example/metadata"),
                URI.create("https://private.example/private"));
        org.mockito.Mockito.verify(http).sendRequest(any(HttpRequest.class), any(RequestOptions.class));
    }

    @Test
    void wellKnownFetchRequiresStatus200WhileResolverFetchAcceptsOtherSuccessStatuses() {
        stubResponses(Map.of("https://mcp.example/metadata", new StubResponse(201, null, "metadata")));
        MetadataDocumentFetcher fetcher = new MetadataDocumentFetcher(
                http, (uri, purpose) -> SuspiciousDestinationGate.Decision.allow(), McpEventLog.noop());
        URI url = URI.create("https://mcp.example/metadata");

        assertThat(fetcher.fetchOk(url, PURPOSE)).isEmpty();

        assertThat(fetcher.fetch(url, PURPOSE)).contains("metadata");
    }

    @Test
    void rejectsRedirectWithMultipleLocationHeaders() {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) 302);
        List<HttpHeader> headers = List.of(locationHeader("https://one.example/metadata"),
                locationHeader("https://two.example/metadata"));
        when(response.headers()).thenReturn(headers);
        HttpRequestResponse requestResponse = responseWith(response);
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenReturn(requestResponse);

        assertThat(new MetadataDocumentFetcher(http,
                (uri, purpose) -> SuspiciousDestinationGate.Decision.allow(), McpEventLog.noop())
                .fetch(URI.create("https://mcp.example/metadata"), PURPOSE)).isEmpty();

        org.mockito.Mockito.verify(http).sendRequest(any(HttpRequest.class), any(RequestOptions.class));
    }

    @Test
    void loopAndThirdRedirectStopWithinSharedPolicyBound() {
        stubResponses(Map.of(
                "https://mcp.example/loop", new StubResponse(302, "/loop", null),
                "https://mcp.example/start", new StubResponse(302, "/one", null),
                "https://mcp.example/one", new StubResponse(302, "/two", null),
                "https://mcp.example/two", new StubResponse(302, "/three", null)));
        List<URI> gated = new ArrayList<>();
        SuspiciousDestinationGate gate = (uri, purpose) -> {
            gated.add(uri);
            return SuspiciousDestinationGate.Decision.allow();
        };
        MetadataDocumentFetcher fetcher = new MetadataDocumentFetcher(http, gate, McpEventLog.noop());

        assertThat(fetcher.fetch(URI.create("https://mcp.example/loop"), PURPOSE)).isEmpty();
        org.mockito.Mockito.verify(http).sendRequest(any(HttpRequest.class), any(RequestOptions.class));

        org.mockito.Mockito.reset(http);
        stubResponses(Map.of(
                "https://mcp.example/start", new StubResponse(302, "/one", null),
                "https://mcp.example/one", new StubResponse(302, "/two", null),
                "https://mcp.example/two", new StubResponse(302, "/three", null)));
        gated.clear();

        assertThat(fetcher.fetch(URI.create("https://mcp.example/start"), PURPOSE)).isEmpty();
        assertThat(gated).containsExactly(URI.create("https://mcp.example/start"),
                URI.create("https://mcp.example/one"), URI.create("https://mcp.example/two"));
        org.mockito.Mockito.verify(http, org.mockito.Mockito.times(3))
                .sendRequest(any(HttpRequest.class), any(RequestOptions.class));
    }

    private void stubResponses(Map<String, StubResponse> responses) {
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            StubResponse stub = responses.get(request.url());
            if (stub == null) {
                return responseWith(null);
            }
            HttpResponse response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn((short) stub.status());
            List<HttpHeader> headers = stub.location() == null ? List.of() : List.of(locationHeader(stub.location()));
            when(response.headers()).thenReturn(headers);
            if (stub.body() != null) {
                burp.api.montoya.core.ByteArray body = burp.api.montoya.core.ByteArray.byteArray(stub.body());
                when(response.bodyToString()).thenReturn(stub.body());
                when(response.body()).thenReturn(body);
            }
            return responseWith(response);
        });
    }

    private HttpHeader locationHeader(String location) {
        HttpHeader header = mock(HttpHeader.class);
        when(header.name()).thenReturn("Location");
        when(header.value()).thenReturn(location);
        return header;
    }

    private static HttpRequestResponse responseWith(HttpResponse response) {
        HttpRequestResponse requestResponse = mock(HttpRequestResponse.class);
        when(requestResponse.response()).thenReturn(response);
        return requestResponse;
    }

    private record StubResponse(int status, String location, String body) {}
}
