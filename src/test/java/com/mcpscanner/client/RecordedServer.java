package com.mcpscanner.client;

import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.mcp.McpObjectMapper;
import org.junit.jupiter.api.Named;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

record RecordedServer(String endpoint, Expectation expected, List<RecordedExchange> exchanges) {
    private static final String RECORDINGS_DIRECTORY = "/recorded-servers";
    private static final String INITIALIZE = "initialize";

    record Expectation(String version, boolean sessionId, int tools) {}

    static Stream<Named<RecordedServer>> catalogue() throws IOException, URISyntaxException {
        Path directory = Path.of(RecordedServer.class.getResource(RECORDINGS_DIRECTORY).toURI());
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted()
                    .map(RecordedServer::named).toList().stream();
        }
    }

    private static Named<RecordedServer> named(Path file) {
        try {
            return Named.of(file.getFileName().toString(),
                    McpObjectMapper.INSTANCE.readValue(file.toFile(), RecordedServer.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    RecordedExchange exchangeFor(String method) {
        return exchanges.stream().filter(exchange -> exchange.method().equals(method)).findFirst()
                .orElseGet(() -> RecordedExchange.methodNotFound(method));
    }

    String path() {
        return URI.create(endpoint).getPath();
    }

    TransportType expectedTransport() {
        return initializeExchange().isPresent() ? TransportType.STREAMABLE_HTTP : TransportType.MODERN_HTTP;
    }

    Optional<String> negotiatedSessionId() {
        return initializeExchange().map(exchange -> exchange.headers().get(McpHeaders.SESSION_ID));
    }

    private Optional<RecordedExchange> initializeExchange() {
        return exchanges.stream().filter(exchange -> exchange.method().equals(INITIALIZE)).findFirst();
    }
}
