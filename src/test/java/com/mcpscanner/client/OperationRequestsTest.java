package com.mcpscanner.client;

import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationRequestsTest {

    private final McpEventLog eventLog = new McpEventLog(null);
    private final OperationRequests operations = new OperationRequests(eventLog);
    private final ProtocolHttpTransport transport = operations.counting((request, timeout) ->
            new ProtocolHttpResponse(202, Map.of(), java.io.InputStream.nullInputStream(), request.uri()));

    @Test
    void logsTheRequestsSentOnTheMeasuringThreadAtDebugLevel() {
        operations.measure("Connect", () -> {
            send();
            send();
        });

        assertThat(debugLines()).singleElement().asString().matches("Connect: 2 requests, \\d+\\.\\d{2} s");
    }

    @Test
    void ignoresRequestsSentOutsideAMeasurementOrOnAnotherThread() {
        send();

        operations.measure("Discovery", () -> CompletableFuture.runAsync(this::send).join());

        assertThat(debugLines()).singleElement().asString().startsWith("Discovery: 0 requests, ");
    }

    @Test
    void logsAFailedOperationToo() {
        assertThatThrownBy(() -> operations.measure("Connect", () -> {
            send();
            throw new IllegalStateException("refused");
        })).hasMessage("refused");

        assertThat(debugLines()).singleElement().asString().startsWith("Connect: 1 request, ");
    }

    @Test
    void returnsTheMeasuredResult() {
        assertThat(operations.measure("Discovery", () -> "catalog")).isEqualTo("catalog");
    }

    private void send() {
        try {
            transport.send(new ProtocolHttpRequest(URI.create("http://127.0.0.1/mcp"), "POST", Map.of(), "{}"),
                    Duration.ofSeconds(1)).close();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> debugLines() {
        return eventLog.snapshot().stream().filter(entry -> entry.level() == McpEventLog.Level.DEBUG)
                .map(McpEventLog.LogEntry::message).toList();
    }
}
