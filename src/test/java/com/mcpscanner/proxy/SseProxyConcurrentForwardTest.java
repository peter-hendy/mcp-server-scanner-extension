package com.mcpscanner.proxy;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.ProxyRequestStubs;
import fi.iki.elonen.NanoHTTPD;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SseProxyConcurrentForwardTest {

    private static final int CONCURRENT_FORWARDS = 4;
    private static final Pattern REQUEST_ID = Pattern.compile("\"id\":(\\d+)");
    private static final String INITIALIZE_REPLY = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":"
            + "\"2025-11-25\",\"capabilities\":{},\"serverInfo\":{\"name\":\"fixture\",\"version\":\"1\"}}}";
    private static final String TOOL_CALL = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"echo\",\"arguments\":{}}}";

    private final ExecutorService scanners = Executors.newFixedThreadPool(CONCURRENT_FORWARDS);
    private final AtomicInteger initializes = new AtomicInteger();
    private final List<String> repliedSessions = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void stopScanners() {
        scanners.shutdownNow();
    }

    @Test
    void managedForwardsReachTheUpstreamTogether() throws Exception {
        CyclicBarrier allInFlight = new CyclicBarrier(CONCURRENT_FORWARDS);

        List<Integer> statuses = forwardConcurrently(upstream(request -> {
            allInFlight.await(5, SECONDS);
            return reply(request);
        }));

        assertThat(statuses).containsOnly(200).hasSize(CONCURRENT_FORWARDS);
    }

    @Test
    void concurrentSessionLossReinitializesOnce() throws Exception {
        CyclicBarrier allSawExpiredSession = new CyclicBarrier(CONCURRENT_FORWARDS);

        List<Integer> statuses = forwardConcurrently(upstream(request -> {
            if (request.headers().firstValue("Mcp-Session-Id").orElse("").equals("session-1")) {
                allSawExpiredSession.await(5, SECONDS);
                return response(404, Map.of(), "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32600}}");
            }
            return reply(request);
        }));

        assertThat(statuses).containsOnly(200).hasSize(CONCURRENT_FORWARDS);
        assertThat(initializes).hasValue(2);
        assertThat(repliedSessions).containsOnly("session-2");
    }

    private List<Integer> forwardConcurrently(HttpClient upstream) throws Exception {
        McpScannerSession session = new McpScannerSession(upstream, new McpEventLog(null));
        session.connect(new McpServerConfig("https://upstream/mcp", TransportType.STREAMABLE_HTTP, new NoAuthStrategy()));
        SseProxyServer proxy = TestSseProxies.builder(session).httpClient(mock(HttpClient.class)).build();
        try {
            List<Future<Integer>> forwards = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_FORWARDS; i++) {
                NanoHTTPD.IHTTPSession request = managedToolCall(proxy);
                forwards.add(scanners.submit(() -> proxy.serve(request).getStatus().getRequestStatus()));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> forward : forwards) {
                statuses.add(forward.get(10, SECONDS));
            }
            return statuses;
        } finally {
            proxy.stop();
            session.disconnect();
        }
    }

    @SuppressWarnings("unchecked")
    private HttpClient upstream(Operation operation) throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            String body = SseProxyServerTest.extractBody(request);
            if (request.method().equals("DELETE")) {
                return response(200, Map.of(), "");
            }
            if (body.contains("\"method\":\"initialize\"")) {
                return response(200, Map.of("Mcp-Session-Id", List.of("session-" + initializes.incrementAndGet())),
                        INITIALIZE_REPLY);
            }
            if (body.contains("notifications/initialized")) {
                return response(202, Map.of(), "");
            }
            return operation.answer(request);
        });
        return http;
    }

    private HttpResponse<InputStream> reply(HttpRequest request) throws Exception {
        repliedSessions.add(request.headers().firstValue("Mcp-Session-Id").orElse(""));
        Matcher id = REQUEST_ID.matcher(SseProxyServerTest.extractBody(request));
        assertThat(id.find()).isTrue();
        return response(200, Map.of(), "{\"jsonrpc\":\"2.0\",\"id\":" + id.group(1) + ",\"result\":{}}");
    }

    private static NanoHTTPD.IHTTPSession managedToolCall(SseProxyServer proxy) throws Exception {
        Map<String, String> baseline = Map.of("Mcp-Session-Id", "session-1");
        Map<String, String> headers = new java.util.HashMap<>(baseline);
        headers.putAll(proxy.captureScanConnection().headers(baseline));
        NanoHTTPD.IHTTPSession request = mock(NanoHTTPD.IHTTPSession.class);
        when(request.getMethod()).thenReturn(NanoHTTPD.Method.POST);
        when(request.getUri()).thenReturn("/mcp");
        ProxyRequestStubs.stubBody(request, headers, TOOL_CALL);
        return request;
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, Map<String, List<String>> extra, String body) {
        Map<String, List<String>> headers = new java.util.HashMap<>(extra);
        headers.put("Content-Type", List.of("application/json"));
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        return response;
    }

    @FunctionalInterface
    private interface Operation {
        HttpResponse<InputStream> answer(HttpRequest request) throws Exception;
    }
}
