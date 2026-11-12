package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerbatimHeaderHttpTest {

    private static final java.time.Duration HELD_OPEN_READ_TIMEOUT = java.time.Duration.ofSeconds(60);

    private static final String SSE_REPLY = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n\n";

    private final AtomicReference<Headers> received = new AtomicReference<>();
    private HttpServer server;
    private String url;

    @BeforeEach
    void startServer() throws IOException {
        MontoyaTestFactory.install();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::replyWithSse);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsAProbeHostHeaderOnTheWire() {
        new VerbatimHeaderHttp().sendRequest(post().withHeader("Host", "attacker.example").withHeader("Origin", "null"));

        assertThat(received.get().getFirst("Host")).isEqualTo("attacker.example");
        assertThat(received.get().getFirst("Origin")).isEqualTo("null");
    }

    @Test
    void closesEachConnectionUnlessTheProbeChoosesItsOwnConnectionHeader() {
        VerbatimHeaderHttp http = new VerbatimHeaderHttp();

        http.sendRequest(post());
        assertThat(received.get().getFirst("Connection")).isEqualTo("close");

        http.sendRequest(post().withHeader("Connection", "keep-alive"));
        assertThat(received.get().getFirst("Connection")).isEqualTo("keep-alive");
    }

    @Test
    void neverReinjectsASessionHeaderAProbeRemoved() {
        VerbatimHeaderHttp http = new VerbatimHeaderHttp();
        HttpRequest withSession = post().withHeader("Mcp-Session-Id", "abc");
        http.sendRequest(withSession);
        HttpHeader session = mock(HttpHeader.class);
        when(session.name()).thenReturn("Mcp-Session-Id");

        http.sendRequest(withSession.withRemovedHeaders(List.of(session)));

        assertThat(received.get().containsKey("Mcp-Session-Id")).isFalse();
    }

    @Test
    void liftsTheJsonRpcReplyOutOfAnEventStream() {
        HttpRequestResponse exchange = new VerbatimHeaderHttp().sendRequest(post());

        assertThat(exchange.response().statusCode()).isEqualTo((short) 200);
        assertThat(exchange.response().bodyToString()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");
        assertThat(exchange.response().headerValue("Content-Type")).isEqualTo("application/json");
    }

    private HttpRequest post() {
        return post(url);
    }

    private static HttpRequest post(String target) {
        return HttpRequest.httpRequestFromUrl(target).withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");
    }

    @Test
    void neverSendsAProbeOverAConnectionTheServerKeptAliveAgainstItsConnectionClose() throws Exception {
        try (OneReplyPerConnectionServer keepAliveServer = new OneReplyPerConnectionServer()) {
            VerbatimHeaderHttp http = new VerbatimHeaderHttp();
            String target = "http://127.0.0.1:" + keepAliveServer.port() + "/mcp";

            List<Integer> statuses = java.util.stream.IntStream.range(0, 3)
                    .mapToObj(attempt -> (int) http.sendRequest(post(target)).response().statusCode())
                    .toList();

            assertThat(statuses).containsOnly(200);
        }
    }

    @Test
    void readsAChunkedReplyFromAServerThatKeepsTheConnectionOpenWithoutWaitingForClose() throws Exception {
        try (ScriptedConnectionServer server = new ScriptedConnectionServer(List.of(ScriptedConnectionServer.raw(
                "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n"
                        + "Connection: keep-alive\r\n\r\n" + chunk(SSE_REPLY))))) {
            long started = System.nanoTime();

            HttpRequestResponse exchange = new VerbatimHeaderHttp(HELD_OPEN_READ_TIMEOUT)
                    .sendRequest(post(server.url("/mcp")));

            assertThat(exchange.response().bodyToString()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - started))
                    .isLessThan(HELD_OPEN_READ_TIMEOUT.dividedBy(2));
        }
    }

    @Test
    void retriesAConnectionDroppedBeforeAnyReply() throws Exception {
        try (ScriptedConnectionServer server = new ScriptedConnectionServer(List.of(ScriptedConnectionServer.DROP,
                ScriptedConnectionServer.raw("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}")))) {

            HttpRequestResponse exchange = new VerbatimHeaderHttp().sendRequest(post(server.url("/mcp")));

            assertThat(exchange.response().statusCode()).isEqualTo((short) 200);
            assertThat(server.connectionCount()).isEqualTo(2);
        }
    }

    @Test
    void neverResendsARequestOnceTheServerHasStartedReplying() throws Exception {
        try (ScriptedConnectionServer server = new ScriptedConnectionServer(List.of(ScriptedConnectionServer.truncated(
                "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n10\r\nonly-half"),
                ScriptedConnectionServer.raw("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}")))) {

            HttpRequestResponse exchange = new VerbatimHeaderHttp().sendRequest(post(server.url("/mcp")));

            assertThat(exchange.response()).isNull();
            assertThat(server.connectionCount()).isEqualTo(1);
        }
    }

    @Test
    void givesUpWithoutRetryWhenTheReadTimeoutPasses() throws Exception {
        try (ScriptedConnectionServer server = new ScriptedConnectionServer(List.of(
                ScriptedConnectionServer.UNTIL_CLOSED))) {

            HttpRequestResponse exchange = new VerbatimHeaderHttp(java.time.Duration.ofMillis(300))
                    .sendRequest(post(server.url("/mcp")));

            assertThat(exchange.response()).isNull();
            assertThat(server.connectionCount()).isEqualTo(1);
        }
    }

    @Test
    void waitsAsLongAsALongerReadTimeoutAllows() throws Exception {
        try (ScriptedConnectionServer server = new ScriptedConnectionServer(List.of(
                ScriptedConnectionServer.UNTIL_CLOSED,
                ScriptedConnectionServer.after(700,
                        ScriptedConnectionServer.raw("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}"))))) {

            assertThat(new VerbatimHeaderHttp(java.time.Duration.ofMillis(300))
                    .sendRequest(post(server.url("/mcp"))).response()).isNull();
            assertThat(new VerbatimHeaderHttp(java.time.Duration.ofSeconds(60))
                    .sendRequest(post(server.url("/mcp"))).response().statusCode()).isEqualTo((short) 200);
        }
    }

    @Test
    void deliversARequestToItsServiceSoARoutedRequestReachesTheProxy() {
        HttpRequest routed = post("http://upstream.invalid:9/mcp").withService(
                burp.api.montoya.http.HttpService.httpService("127.0.0.1", server.getAddress().getPort(), false));

        HttpRequestResponse exchange = new VerbatimHeaderHttp().sendRequest(routed);

        assertThat(exchange.response().statusCode()).isEqualTo((short) 200);
        assertThat(received.get().getFirst("Host")).isEqualTo("upstream.invalid:9");
    }

    private static String chunk(String data) {
        return Integer.toHexString(data.getBytes(StandardCharsets.UTF_8).length) + "\r\n" + data + "\r\n0\r\n\r\n";
    }

    private void replyWithSse(HttpExchange exchange) throws IOException {
        received.set(exchange.getRequestHeaders());
        exchange.getRequestBody().readAllBytes();
        byte[] body = SSE_REPLY.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
