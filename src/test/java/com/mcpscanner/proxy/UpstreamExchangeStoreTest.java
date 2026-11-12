package com.mcpscanner.proxy;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamExchangeStoreTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void storedExchangeIsReturnedForAProxiedResponseCarryingItsId() {
        UpstreamExchangeStore store = new UpstreamExchangeStore();
        String id = store.put(exchange("{\"jsonrpc\":\"2.0\",\"id\":101}", "{\"result\":{}}"));

        HttpRequestResponse upstream = store.upstreamOf(proxiedCarrying(id)).orElseThrow();

        assertThat(upstream.request().url()).isEqualTo("https://upstream.test/mcp");
        assertThat(upstream.request().method()).isEqualTo("POST");
        assertThat(upstream.request().headerValue("Authorization")).isEqualTo("Bearer secret");
        assertThat(upstream.request().bodyToString()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":101}");
        assertThat(upstream.response().statusCode()).isEqualTo((short) 200);
        assertThat(upstream.response().headerValue("Mcp-Session-Id")).isEqualTo("upstream-session");
        assertThat(upstream.response().bodyToString()).isEqualTo("{\"result\":{}}");
    }

    @Test
    void evidenceResponseDropsFramingHeadersAndStatesItsOwnLength() {
        UpstreamExchangeStore store = new UpstreamExchangeStore();
        RecordedExchange chunked = new RecordedExchange(URI.create("https://upstream.test/mcp"), "POST", Map.of(),
                "{}", 200, Map.of("Transfer-Encoding", List.of("chunked"), "Content-Length", List.of("999")),
                "abc".getBytes(StandardCharsets.UTF_8));

        HttpResponse response = store.upstreamOf(proxiedCarrying(store.put(chunked))).orElseThrow().response();

        assertThat(response.headerValue("Transfer-Encoding")).isNull();
        assertThat(response.headerValue("Content-Length")).isEqualTo("3");
    }

    @Test
    void unknownIdMissingHeaderAndMissingResponseFindNothing() {
        UpstreamExchangeStore store = new UpstreamExchangeStore();
        store.put(exchange("{}", "{}"));

        assertThat(store.upstreamOf(proxiedCarrying("not-an-issued-id"))).isEmpty();
        assertThat(store.upstreamOf(proxied(HttpResponse.httpResponse("HTTP/1.1 200\r\n\r\n{}")))).isEmpty();
        assertThat(store.upstreamOf(proxied(null))).isEmpty();
    }

    @Test
    void oldestExchangeIsEvictedOnceTheEntryBoundIsReached() {
        UpstreamExchangeStore store = new UpstreamExchangeStore(2, Long.MAX_VALUE);
        String oldest = store.put(exchange("{}", "{}"));
        String middle = store.put(exchange("{}", "{}"));
        String newest = store.put(exchange("{}", "{}"));

        assertThat(store.upstreamOf(proxiedCarrying(oldest))).isEmpty();
        assertThat(store.upstreamOf(proxiedCarrying(middle))).isPresent();
        assertThat(store.upstreamOf(proxiedCarrying(newest))).isPresent();
    }

    @Test
    void oldestExchangesAreEvictedOnceTheBodyByteBoundIsExceeded() {
        UpstreamExchangeStore store = new UpstreamExchangeStore(100, 10);
        String oldest = store.put(exchange("123", "456"));
        String newest = store.put(exchange("123", "456"));

        assertThat(store.upstreamOf(proxiedCarrying(oldest))).isEmpty();
        assertThat(store.upstreamOf(proxiedCarrying(newest))).isPresent();
    }

    @Test
    void anExchangeLargerThanTheWholeBudgetIsNotKept() {
        UpstreamExchangeStore store = new UpstreamExchangeStore(100, 4);

        assertThat(store.upstreamOf(proxiedCarrying(store.put(exchange("123", "456"))))).isEmpty();
    }

    @Test
    void defaultBoundsAreFourThousandExchangesAndThirtyTwoMebibytesOfBodies() {
        assertThat(UpstreamExchangeStore.MAX_EXCHANGES).isEqualTo(4_096);
        assertThat(UpstreamExchangeStore.MAX_BODY_BYTES).isEqualTo(32L * 1024 * 1024);
    }

    @Test
    void idsAreOpaqueUniqueHexStringsEvenUnderConcurrentPuts() throws Exception {
        UpstreamExchangeStore store = new UpstreamExchangeStore();
        Map<String, Boolean> ids = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        IntStream.range(0, 400).forEach(i -> pool.submit(() -> ids.put(store.put(exchange("{}", "{}")), true)));
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(ids).hasSize(400);
        assertThat(ids.keySet()).allMatch(id -> id.matches("[0-9a-f]{32}"));
        assertThat(ids.keySet()).allMatch(id -> store.upstreamOf(proxiedCarrying(id)).isPresent());
    }

    private static RecordedExchange exchange(String requestBody, String responseBody) {
        return new RecordedExchange(URI.create("https://upstream.test/mcp"), "POST",
                Map.of("Authorization", List.of("Bearer secret")), requestBody, 200,
                Map.of("Mcp-Session-Id", List.of("upstream-session")), responseBody.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpRequestResponse proxiedCarrying(String id) {
        return proxied(HttpResponse.httpResponse("HTTP/1.1 200\r\n" + ScannerSentinels.EXCHANGE_HEADER + ": " + id
                + "\r\n\r\n{}"));
    }

    private static HttpRequestResponse proxied(HttpResponse response) {
        return HttpRequestResponse.httpRequestResponse(HttpRequest.httpRequestFromUrl("http://127.0.0.1:1/mcp"),
                response);
    }
}
