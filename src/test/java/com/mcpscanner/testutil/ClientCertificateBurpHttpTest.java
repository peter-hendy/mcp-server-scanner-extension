package com.mcpscanner.testutil;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class ClientCertificateBurpHttpTest {

    private final AtomicReference<byte[]> receivedBody = new AtomicReference<>();
    private final CountDownLatch releaseSlowReply = new CountDownLatch(1);
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        MontoyaTestFactory.install();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/echo", this::recordBody);
        server.createContext("/redirect", exchange -> reply(exchange, 302, "/target"));
        server.createContext("/target", exchange -> reply(exchange, 200, null));
        server.createContext("/slow", this::replyWhenReleased);
        server.start();
    }

    @AfterEach
    void stopServer() {
        releaseSlowReply.countDown();
        server.stop(0);
    }

    @Test
    void sendsTheRequestBodyBytesUnchanged() throws Exception {
        byte[] utf8 = "{\"text\":\"café ☃ 🚀\"}".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.httpRequestFromUrl(url("/echo"))
                .withMethod("POST")
                .withBody(ByteArray.byteArray(utf8));

        HttpRequestResponse sent = burp().sendRequest(request,
                RequestOptions.requestOptions().withRedirectionMode(RedirectionMode.NEVER));

        assertThat(sent.response().statusCode()).isEqualTo((short) 200);
        assertThat(receivedBody.get()).isEqualTo(utf8);
    }

    @Test
    void returnsTheRedirectWhenRedirectionModeIsNever() throws Exception {
        HttpRequestResponse sent = burp().sendRequest(get("/redirect"),
                RequestOptions.requestOptions().withRedirectionMode(RedirectionMode.NEVER));

        assertThat(sent.response().statusCode()).isEqualTo((short) 302);
    }

    @Test
    void followsTheRedirectWhenRedirectionModeIsAlways() throws Exception {
        HttpRequestResponse sent = burp().sendRequest(get("/redirect"),
                RequestOptions.requestOptions().withRedirectionMode(RedirectionMode.ALWAYS));

        assertThat(sent.response().statusCode()).isEqualTo((short) 200);
    }

    @Test
    void givesUpAfterTheRequestedResponseTimeoutWithANullResponse() throws Exception {
        long started = System.nanoTime();

        HttpRequestResponse sent = burp().sendRequest(get("/slow"),
                RequestOptions.requestOptions().withRedirectionMode(RedirectionMode.NEVER).withResponseTimeout(200));

        assertThat(sent.response()).isNull();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }

    private static ClientCertificateBurpHttp burp() throws Exception {
        return ClientCertificateBurpHttp.withoutClientCertificate();
    }

    private HttpRequest get(String path) {
        return HttpRequest.httpRequestFromUrl(url(path)).withMethod("GET");
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private void recordBody(HttpExchange exchange) throws IOException {
        receivedBody.set(exchange.getRequestBody().readAllBytes());
        reply(exchange, 200, null);
    }

    private void replyWhenReleased(HttpExchange exchange) throws IOException {
        try {
            releaseSlowReply.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        reply(exchange, 200, null);
    }

    private static void reply(HttpExchange exchange, int status, String location) throws IOException {
        if (location != null) {
            exchange.getResponseHeaders().add("Location", location);
        }
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}
