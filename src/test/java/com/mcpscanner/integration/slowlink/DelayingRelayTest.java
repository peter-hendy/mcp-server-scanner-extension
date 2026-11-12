package com.mcpscanner.integration.slowlink;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DelayingRelayTest {

    private static final Duration ONE_WAY = Duration.ofMillis(100);
    private static final Duration ROUND_TRIP = ONE_WAY.multipliedBy(2);

    private final Set<Integer> clientPorts = ConcurrentHashMap.newKeySet();
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private HttpServer echo;
    private DelayingRelay relay;

    @BeforeEach
    void start() throws IOException {
        echo = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        echo.createContext("/", this::echoBody);
        echo.setExecutor(Executors.newCachedThreadPool());
        echo.start();
        relay = DelayingRelay.listening(ONE_WAY);
        relay.forwardTo(echo.getAddress());
    }

    @AfterEach
    void stop() throws IOException {
        relay.close();
        echo.stop(0);
    }

    @Test
    void reservesItsPortBeforeItKnowsWhereToForwardSoTheTargetCanAdvertiseIt() throws Exception {
        try (DelayingRelay reserved = DelayingRelay.listening(ONE_WAY)) {
            assertThat(reserved.baseUrl()).isEqualTo("http://127.0.0.1:" + reserved.port());
            assertThat(reserved.port()).isPositive();
        }
    }

    @Test
    void eachExchangeTakesAtLeastOneRoundTripAndIsCounted() throws Exception {
        long started = System.nanoTime();

        send("ping");

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(ROUND_TRIP);
        assertThat(relay.requestsSeen()).isEqualTo(1);
    }

    @Test
    void keepsKeepAliveConnectionsOpenAcrossRequests() throws Exception {
        send("first");
        send("second");

        assertThat(relay.requestsSeen()).isEqualTo(2);
        assertThat(clientPorts).hasSize(1);
    }

    @Test
    void deliversLargeBodiesByteForByteInOrder() throws Exception {
        byte[] body = new byte[2 * 1024 * 1024];
        new Random(7).nextBytes(body);

        HttpResponse<byte[]> echoed = client.send(post(HttpRequest.BodyPublishers.ofByteArray(body)),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(echoed.body()).isEqualTo(body);
    }

    @Test
    void delaysEachConnectionWithoutHoldingUpTheOthers() {
        int connections = 8;
        HttpClient[] clients = IntStream.range(0, connections).mapToObj(index -> HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).build()).toArray(HttpClient[]::new);
        long started = System.nanoTime();

        List<String> replies = IntStream.range(0, connections).mapToObj(index -> clients[index]
                .sendAsync(post(HttpRequest.BodyPublishers.ofString("n" + index)),
                        HttpResponse.BodyHandlers.ofString())).toList()
                .stream().map(reply -> reply.join().body()).toList();

        assertThat(replies).hasSize(connections);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(ROUND_TRIP.multipliedBy(3));
        assertThat(relay.requestsSeen()).isEqualTo(connections);
    }

    private void send(String body) throws Exception {
        HttpResponse<String> reply = client.send(post(HttpRequest.BodyPublishers.ofString(body)),
                HttpResponse.BodyHandlers.ofString());
        assertThat(reply.body()).isEqualTo(body);
    }

    private HttpRequest post(HttpRequest.BodyPublisher body) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + relay.port() + "/echo")).POST(body).build();
    }

    private void echoBody(HttpExchange exchange) throws IOException {
        clientPorts.add(exchange.getRemoteAddress().getPort());
        byte[] body = exchange.getRequestBody().readAllBytes();
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
