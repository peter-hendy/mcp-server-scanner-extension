package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.requests.ArmEndpoint;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

final class ReadinessRequests {

    static final String SESSION_HEADER = "Mcp-Session-Id";

    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(ATTEMPT_TIMEOUT)
            .build();

    HttpResponse<String> post(ArmEndpoint endpoint, String body, Optional<String> session) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint.uri())
                .timeout(ATTEMPT_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        endpoint.auth().headers().forEach(builder::header);
        session.ifPresent(id -> builder.header(SESSION_HEADER, id));
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for " + endpoint.uri(), e);
        }
    }
}
