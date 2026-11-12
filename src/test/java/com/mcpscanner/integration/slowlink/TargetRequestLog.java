package com.mcpscanner.integration.slowlink;

import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class TargetRequestLog {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final URI requests;

    public TargetRequestLog(String targetBaseUrl) {
        this.requests = URI.create(targetBaseUrl + "/_test/requests");
    }

    public void clear() throws IOException, InterruptedException {
        client.send(HttpRequest.newBuilder(requests).timeout(TIMEOUT).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    public int received() throws IOException, InterruptedException {
        String body = client.send(HttpRequest.newBuilder(requests).timeout(TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
        return McpObjectMapper.INSTANCE.readTree(body).size();
    }
}
