package com.mcpscanner.cvetargets.run;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SseEndpointAttemptTest {

    private final CountDownLatch streamsReleased = new CountDownLatch(1);
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        MontoyaTestFactory.install();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sse", this::holdStreamOpenAfterTheEndpointEvent);
        server.createContext("/locked", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        streamsReleased.countDown();
        server.stop(0);
    }

    @Test
    void isReadyAsSoonAsTheStreamAnnouncesItsMessageEndpoint() {
        Instant started = Instant.now();

        assertThat(new SseEndpointAttempt().notReadyBecause(endpoint("/sse"))).isEmpty();
        assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void reportsARefusedStream() {
        assertThat(new SseEndpointAttempt().notReadyBecause(endpoint("/locked"))).contains("status 401");
    }

    private ArmEndpoint endpoint(String path) {
        return new ArmEndpoint(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path),
                new NoAuthStrategy(), TransportType.SSE);
    }

    private void holdStreamOpenAfterTheEndpointEvent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write("event: endpoint\ndata: /messages?sessionId=1\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            streamsReleased.await(4, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
