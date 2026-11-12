package com.mcpscanner.client.servercard;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.mcp.BoundedBodyReader;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerCardFetcherTest {
    private static final URI ENDPOINT = URI.create("https://mcp.example/mcp");
    private static final String CARD_URL = "https://mcp.example/mcp/server-card";
    private static final String CATALOG_URL = "https://mcp.example/.well-known/ai-catalog.json";

    private Http http;
    private ServerCardFetcher fetcher;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        http = mock(Http.class);
        fetcher = new ServerCardFetcher(http);
    }

    @Test
    void foundDocumentsArePrettyPrintedJson() {
        stub(Map.of(
                CARD_URL, response(200, "{\"name\":\"io.example/server\",\"version\":\"1.0.0\"}"),
                CATALOG_URL, response(200, "{\"servers\":[]}")));

        ServerCardReport report = fetcher.fetch(ENDPOINT);

        assertThat(report.serverCard()).isInstanceOf(DocumentOutcome.Found.class);
        DocumentOutcome.Found card = (DocumentOutcome.Found) report.serverCard();
        assertThat(card.url()).isEqualTo(URI.create(CARD_URL));
        assertThat(card.document().path("name").asText()).isEqualTo("io.example/server");
        assertThat(report.aiCatalog()).isInstanceOf(DocumentOutcome.Found.class);
        assertThat(report.aiCatalog().url()).isEqualTo(URI.create(CATALOG_URL));
    }

    @Test
    void nonSuccessStatusIsNotFoundWithStatusCode() {
        stub(Map.of(
                CARD_URL, response(404, "missing"),
                CATALOG_URL, response(302, "")));

        ServerCardReport report = fetcher.fetch(ENDPOINT);

        assertThat(report.serverCard()).isEqualTo(new DocumentOutcome.NotFound(URI.create(CARD_URL), 404));
        assertThat(report.aiCatalog()).isEqualTo(new DocumentOutcome.NotFound(URI.create(CATALOG_URL), 302));
    }

    @Test
    void nonJsonSuccessBodyIsFailedNotJson() {
        stub(Map.of(
                CARD_URL, response(200, "<html>hello</html>"),
                CATALOG_URL, response(200, "")));

        ServerCardReport report = fetcher.fetch(ENDPOINT);

        assertThat(report.serverCard()).isEqualTo(new DocumentOutcome.Failed(URI.create(CARD_URL), "not JSON"));
        assertThat(report.aiCatalog()).isEqualTo(new DocumentOutcome.Failed(URI.create(CATALOG_URL), "not JSON"));
    }

    @Test
    void oversizedBodyIsFailed() {
        HttpResponse oversized = mock(HttpResponse.class);
        when(oversized.statusCode()).thenReturn((short) 200);
        ByteArray body = mock(ByteArray.class);
        when(body.length()).thenReturn(BoundedBodyReader.MAX_RESPONSE_BYTES + 1);
        when(oversized.body()).thenReturn(body);
        stub(Map.of(CARD_URL, oversized));

        DocumentOutcome outcome = fetcher.fetch(ENDPOINT).serverCard();

        assertThat(outcome).isInstanceOf(DocumentOutcome.Failed.class);
        assertThat(((DocumentOutcome.Failed) outcome).reason()).contains("exceeded");
    }

    @Test
    void transportExceptionIsFailedWithReason() {
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenThrow(new RuntimeException("Read timed out"));

        ServerCardReport report = fetcher.fetch(ENDPOINT);

        assertThat(report.serverCard()).isEqualTo(new DocumentOutcome.Failed(URI.create(CARD_URL), "Read timed out"));
        assertThat(report.aiCatalog()).isEqualTo(new DocumentOutcome.Failed(URI.create(CATALOG_URL), "Read timed out"));
    }

    @Test
    void missingResponseIsFailedNoResponse() {
        stub(Map.of());

        ServerCardReport report = fetcher.fetch(ENDPOINT);

        assertThat(report.serverCard()).isEqualTo(new DocumentOutcome.Failed(URI.create(CARD_URL), "no response"));
    }

    @Test
    void sendsOnlyAcceptHeaderAndNoCredentials() {
        stub(Map.of());

        fetcher.fetch(URI.create("https://user:pw@mcp.example/mcp?api_key=secret"));

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).sendRequest(requests.capture(), any(RequestOptions.class));
        assertThat(requests.getAllValues()).extracting(HttpRequest::url)
                .containsExactly(CARD_URL, CATALOG_URL);
        for (HttpRequest request : requests.getAllValues()) {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.headers()).extracting(HttpHeader::name)
                    .contains("Accept")
                    .doesNotContain("Authorization", "Cookie", "User-Agent");
            assertThat(request.headerValue("Accept")).isEqualTo("application/json");
        }
    }

    private void stub(Map<String, HttpResponse> responses) {
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            HttpResponse response = responses.get(request.url());
            HttpRequestResponse requestResponse = mock(HttpRequestResponse.class);
            when(requestResponse.response()).thenReturn(response);
            return requestResponse;
        });
    }

    private static HttpResponse response(int status, String body) {
        ByteArray bodyBytes = ByteArray.byteArray(body);
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) status);
        when(response.body()).thenReturn(bodyBytes);
        when(response.bodyToString()).thenReturn(body);
        when(response.headers()).thenReturn(List.of());
        return response;
    }
}
