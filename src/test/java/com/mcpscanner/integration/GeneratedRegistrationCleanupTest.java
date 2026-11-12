package com.mcpscanner.integration;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.mcpscanner.checks.shared.oauth.AbandonedClientLog;
import com.mcpscanner.checks.shared.oauth.ObservedRegistrationCleanup;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.scan.request.ClientRegistrationMessage;
import com.mcpscanner.scan.request.ClientRegistrationRequestFactory;
import com.mcpscanner.scan.request.GeneratedRegistrationCleanupHandler;
import com.mcpscanner.scan.request.RequestFamily;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ServerCapabilities;
import com.mcpscanner.scan.request.ServerSurface;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RecordingRealHttp;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class GeneratedRegistrationCleanupTest {

    private final FakeRegistrationServer server = new FakeRegistrationServer();
    private final RecordingRealHttp http = new RecordingRealHttp();
    private final GeneratedRegistrationCleanupHandler handler = new GeneratedRegistrationCleanupHandler(
            new ObservedRegistrationCleanup(http, Clock.systemUTC(), (delay, task) -> { },
                    new AbandonedClientLog(McpEventLog.noop()))::deleteClientRegisteredBy,
            (registration, cleanup) -> cleanup.deleteClientRegisteredBy(registration));
    private final AtomicInteger messageIds = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        MontoyaTestFactory.install();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void deletesTheClientOfTheGeneratedRegistrationAndOfEveryFuzzedVariant() {
        HttpRequestResponse base = sendThroughBurp(generatedRegistration());
        List<HttpRequest> variants = new McpInsertionPointProvider().provideInsertionPoints(base).stream()
                .filter(point -> point.name().endsWith("_uri"))
                .map(GeneratedRegistrationCleanupTest::withCanaryPayload)
                .toList();
        variants.forEach(this::sendThroughBurp);

        assertThat(variants).hasSizeGreaterThanOrEqualTo(4);
        assertThat(server.registered()).isEqualTo(1 + variants.size());
        assertThat(server.liveClients()).isEmpty();
        assertThat(server.markedRegistrations()).isZero();
    }

    @Test
    void leavesARegistrationTheScannerDidNotGenerateAlone() {
        sendThroughBurp(generatedRegistration().withRemovedHeader(ScannerSentinels.GENERATED_REGISTRATION_HEADER));

        assertThat(server.liveClients()).hasSize(1);
        assertThat(server.deletionAttempts()).isZero();
    }

    @Test
    void sendsNoCleanupToAServerThatAnsweredTooManyRequests() {
        server.throttle(true);
        sendThroughBurp(generatedRegistration());
        server.throttle(false);

        sendThroughBurp(generatedRegistration());

        assertThat(server.liveClients()).hasSize(1);
        assertThat(server.deletionAttempts()).isZero();
    }

    private HttpRequest generatedRegistration() {
        ServerSurface surface = new ServerSurface(new ScanInventory(List.of(), List.of(), List.of(), List.of()),
                ServerCapabilities.none(), null, () -> Optional.of(server.registrationEndpoint()));
        ClientRegistrationMessage message = (ClientRegistrationMessage) RequestGenerator.standard()
                .scanAll(surface, family -> family == RequestFamily.CLIENT_REGISTRATION).messages().get(0);
        return new ClientRegistrationRequestFactory().build(message);
    }

    private static HttpRequest withCanaryPayload(AuditInsertionPoint point) {
        return point.buildHttpRequestWithPayload(ByteArray.byteArray("https://canary.example.test/" + point.name()));
    }

    private HttpRequestResponse sendThroughBurp(HttpRequest request) {
        int messageId = messageIds.incrementAndGet();
        HttpRequestToBeSent outgoing = mock(HttpRequestToBeSent.class, delegatesTo(request));
        doReturn(messageId).when(outgoing).messageId();
        HttpRequest onWire = handler.handleHttpRequestToBeSent(outgoing).request();
        HttpRequestResponse exchange = http.sendRequest(onWire);
        HttpResponseReceived received = mock(HttpResponseReceived.class, delegatesTo(exchange.response()));
        doReturn(messageId).when(received).messageId();
        doReturn(onWire).when(received).initiatingRequest();
        handler.handleHttpResponseReceived(received);
        return HttpRequestResponse.httpRequestResponse(request, exchange.response());
    }

    private static final class FakeRegistrationServer {

        private static final String REGISTER = "/register";

        private final Map<String, String> tokensByClient = new ConcurrentHashMap<>();
        private final Set<String> liveClients = ConcurrentHashMap.newKeySet();
        private final List<String> deletions = new CopyOnWriteArrayList<>();
        private final AtomicInteger registered = new AtomicInteger();
        private final AtomicInteger marked = new AtomicInteger();
        private final AtomicBoolean throttled = new AtomicBoolean();
        private HttpServer httpServer;

        void start() throws IOException {
            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            httpServer.createContext(REGISTER, this::handle);
            httpServer.start();
        }

        void stop() {
            httpServer.stop(0);
        }

        URI registrationEndpoint() {
            return URI.create(origin() + REGISTER);
        }

        void throttle(boolean on) {
            throttled.set(on);
        }

        int registered() {
            return registered.get();
        }

        int markedRegistrations() {
            return marked.get();
        }

        Set<String> liveClients() {
            return Set.copyOf(liveClients);
        }

        int deletionAttempts() {
            return deletions.size();
        }

        private String origin() {
            return "http://127.0.0.1:" + httpServer.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            exchange.getRequestBody().readAllBytes();
            if (throttled.get()) {
                reply(exchange, 429, "");
            } else if ("POST".equals(exchange.getRequestMethod())) {
                register(exchange);
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                delete(exchange);
            } else {
                reply(exchange, 405, "");
            }
        }

        private void register(HttpExchange exchange) throws IOException {
            if (exchange.getRequestHeaders().containsKey(ScannerSentinels.GENERATED_REGISTRATION_HEADER)) {
                marked.incrementAndGet();
            }
            String clientId = "c" + registered.incrementAndGet();
            String token = "rat-" + clientId;
            tokensByClient.put(clientId, token);
            liveClients.add(clientId);
            reply(exchange, 201, "{\"client_id\":\"" + clientId + "\",\"registration_client_uri\":\""
                    + origin() + REGISTER + "/" + clientId + "\",\"registration_access_token\":\"" + token + "\"}");
        }

        private void delete(HttpExchange exchange) throws IOException {
            String clientId = exchange.getRequestURI().getPath().substring(REGISTER.length() + 1);
            deletions.add(clientId);
            String expected = "Bearer " + tokensByClient.get(clientId);
            if (expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    && liveClients.remove(clientId)) {
                reply(exchange, 204, "");
            } else {
                reply(exchange, 401, "");
            }
        }

        private static void reply(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        }
    }
}
