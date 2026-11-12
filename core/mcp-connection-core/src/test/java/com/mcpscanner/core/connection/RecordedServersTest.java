package com.mcpscanner.core.connection;

import com.mcpscanner.core.protocol.JsonDocuments;
import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpReplyCodec;
import com.mcpscanner.core.protocol.McpRequests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class RecordedServersTest {
    private static final Path RECORDINGS = Path.of("src/test/resources/recorded-servers");
    private static final int MAX_BYTES = 8 << 20;
    private static final int MAX_EVENTS = 200;

    public static void main(String[] args) throws IOException {
        List<Path> recordings = recordingFiles();
        if (recordings.isEmpty()) throw new AssertionError("No recorded servers in " + RECORDINGS.toAbsolutePath());
        for (Path recording : recordings) replays(Recording.load(recording));
        System.out.println("RecordedServersTest passed: " + recordings.size() + " recorded servers");
    }

    private static List<Path> recordingFiles() throws IOException {
        try (Stream<Path> files = Files.list(RECORDINGS)) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static void replays(Recording recording) {
        var server = new RecordedServer(recording);
        var session = new McpConnection(new McpFlow(MAX_BYTES, MAX_EVENTS), CredentialProviders.none())
            .openHttpSession(server, "recorded-replay", "1");
        var listed = session.send(session.session().request("tools/list", null));
        var tools = (List<?>) new McpReplyCodec(MAX_BYTES, MAX_EVENTS).parse(listed, 1).result().get("tools");
        server.requireAllExchangesUsed();
        check(recording, "version", recording.version(), session.session().version());
        check(recording, "session ID present", recording.sessionId(), session.session().sessionId() != null);
        check(recording, "tool count", recording.tools(), tools.size());
    }

    private static void check(Recording recording, String property, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(recording.name() + ": expected " + property + " " + expected + " but was " + actual);
        }
    }

    private record Exchange(String method, McpFlow.HttpResponse response) {
        @SuppressWarnings("unchecked")
        static Exchange of(Map<String, Object> fields) {
            var headers = (Map<String, String>) fields.get("headers");
            int status = ((Number) fields.get("status")).intValue();
            return new Exchange((String) fields.get("method"),
                new McpFlow.HttpResponse(status, headers, (String) fields.get("body"), true));
        }
    }

    private record Recording(String name, String version, boolean sessionId, int tools, List<Exchange> exchanges) {
        @SuppressWarnings("unchecked")
        static Recording load(Path file) throws IOException {
            var document = (Map<String, Object>) JsonDocuments.parse(Files.readString(file), MAX_BYTES);
            var expected = (Map<String, Object>) document.get("expected");
            var exchanges = ((List<Map<String, Object>>) document.get("exchanges")).stream().map(Exchange::of).toList();
            return new Recording(file.getFileName().toString(), (String) expected.get("version"),
                (Boolean) expected.get("sessionId"), ((Number) expected.get("tools")).intValue(), exchanges);
        }
    }

    private static final class RecordedServer implements McpFlow.HttpPort {
        private final String name;
        private final Deque<Exchange> remaining;

        RecordedServer(Recording recording) {
            this.name = recording.name();
            this.remaining = new ArrayDeque<>(recording.exchanges());
        }

        @Override
        public McpFlow.HttpResponse send(McpRequests.Request request) {
            var next = remaining.poll();
            String method = methodOf(request);
            if (next == null || !next.method().equals(method)) {
                throw new AssertionError(name + ": client sent " + method + " but the recording expected "
                    + (next == null ? "nothing" : next.method()));
            }
            return next.response();
        }

        void requireAllExchangesUsed() {
            if (!remaining.isEmpty()) throw new AssertionError(name + ": unused recorded exchanges " + remaining);
        }

        @SuppressWarnings("unchecked")
        private static String methodOf(McpRequests.Request request) {
            return (String) ((Map<String, Object>) JsonDocuments.parse(request.body(), MAX_BYTES)).get("method");
        }
    }
}
