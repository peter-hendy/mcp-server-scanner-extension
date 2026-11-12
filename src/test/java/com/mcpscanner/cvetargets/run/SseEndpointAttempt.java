package com.mcpscanner.cvetargets.run;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.http.wire.SocketHttpTransport;
import com.mcpscanner.http.wire.WireExchange;
import com.mcpscanner.http.wire.WireRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public final class SseEndpointAttempt implements ReadinessAttempt {

    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(5);
    private static final String ENDPOINT_EVENT = "event:endpoint";

    private final SocketHttpTransport transport = new SocketHttpTransport(ATTEMPT_TIMEOUT);

    @Override
    public Optional<String> notReadyBecause(ArmEndpoint endpoint) {
        try (WireExchange exchange = transport.exchange(WireRequest.of(streamRequest(endpoint)))) {
            int status = exchange.response().status();
            if (status / 100 != 2) {
                return Optional.of("status " + status);
            }
            return announcesEndpoint(exchange) ? Optional.empty() : Optional.of("stream ended without an endpoint event");
        } catch (IOException | RuntimeException e) {
            return Optional.of(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static HttpRequest streamRequest(ArmEndpoint endpoint) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(endpoint.uri().toString())
                .withHeader("Accept", "text/event-stream");
        for (Map.Entry<String, String> header : endpoint.auth().headers().entrySet()) {
            request = request.withHeader(header.getKey(), header.getValue());
        }
        return request;
    }

    private static boolean announcesEndpoint(WireExchange exchange) throws IOException {
        BufferedReader events = new BufferedReader(new InputStreamReader(exchange.response().body(), StandardCharsets.UTF_8));
        for (String line = events.readLine(); line != null; line = events.readLine()) {
            if (line.replace(" ", "").equals(ENDPOINT_EVENT)) {
                return true;
            }
        }
        return false;
    }
}
