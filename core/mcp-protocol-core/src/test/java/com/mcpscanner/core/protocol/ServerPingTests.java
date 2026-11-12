package com.mcpscanner.core.protocol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ServerPingTests {
    private static final String PING = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":%s,\"method\":\"ping\"}\n\n";
    private static final String NOTIFICATION = "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{}}\n\n";
    private static final String SAMPLING = "data: {\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"sampling/createMessage\",\"params\":{}}\n\n";
    private static final String REPLY = "data: {\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":{\"tools\":[]}}\n\n";

    private ServerPingTests() {}

    static void run() {
        pingBeforeReplyIsAnsweredWithSessionHeaders();
        stringAndNumericPingIdsArePreserved();
        replyReturnsWithoutWaitingForStreamEnd();
        disabledHandlerSendsNothing();
        eventCapsStillApply();
        inputStreamReaderStopsAtMatchingReply();
        inputStreamReaderEnforcesByteCap();
        completeEventStreamResponseAnswersPings();
        legacySseCallAnswersPing();
        legacySseOpenAnswersPingBeforeInitializeReply();
        pingReusingRequestIdIsAnsweredNotReturned();
        malformedServerRequestIdsAreSkipped();
        nullHandlerFactoryMeansDisabled();
        unbufferedStreamIsReadInBlocks();
    }

    private static void pingReusingRequestIdIsAnsweredNotReturned() {
        var session = new McpFlow.Session("2025-06-18", null, null, "client", "1");
        List<McpRequests.Request> sent = new ArrayList<>();
        var events = finite(String.format(PING, "7"), String.format(REPLY, "7"));
        var reply = new McpReplyCodec(4096, 8).read(events, McpRequestId.of(7), new PingResponder(session), sent::add);
        equal(true, reply.result().containsKey("tools"));
        equal(1, sent.size());
    }

    private static void malformedServerRequestIdsAreSkipped() {
        for (String id : List.of("1.5", "true", "{}", "[]")) {
            List<McpRequests.Request> sent = new ArrayList<>();
            var session = new McpFlow.Session("2025-06-18", null, null, "client", "1");
            var events = finite(String.format(PING, id), String.format(REPLY, "7"));
            equal(McpRequestId.of(7), new McpReplyCodec(4096, 8)
                .read(events, McpRequestId.of(7), new PingResponder(session), sent::add).id());
            equal(0, sent.size());
            equal(McpRequestId.of(7), new McpReplyCodec(4096, 8)
                .read(finite(String.format(PING, id), String.format(REPLY, "7")), McpRequestId.of(7),
                    ServerRequestHandler.disabled(), sent::add).id());
        }
    }

    private static void nullHandlerFactoryMeansDisabled() {
        var events = new ArrayDeque<>(List.of("event: endpoint\ndata: /messages\n\n", String.format(PING, "1"),
            "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}\n\n"));
        List<String> posts = new ArrayList<>();
        new McpFlow(4096, 8).openSse(ssePort(events, posts), "https://fixture.example", ClientProfile.of("client", "1"), session -> null);
        equal(false, posts.stream().anyMatch(body -> body.contains("\"result\"")));
    }

    private static void unbufferedStreamIsReadInBlocks() {
        byte[] wire = (String.format(PING, "1").repeat(20) + String.format(REPLY, "7")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var reads = new int[1];
        var stream = new java.io.ByteArrayInputStream(wire) {
            @Override public synchronized int read() { reads[0]++; return super.read(); }
            @Override public synchronized int read(byte[] buffer, int offset, int length) { reads[0]++; return super.read(buffer, offset, length); }
        };
        new McpReplyCodec(65536, 64).read(new SseEventReader(stream, 65536), McpRequestId.of(7),
            ServerRequestHandler.disabled(), request -> { });
        equal(true, reads[0] < wire.length / 4);
    }

    private static void pingBeforeReplyIsAnsweredWithSessionHeaders() {
        var session = new McpFlow.Session("2025-06-18", "sid-1", null, "client", "1");
        List<McpRequests.Request> sent = new ArrayList<>();
        var events = finite(String.format(PING, "\"p-1\""), NOTIFICATION, SAMPLING, String.format(REPLY, "5"));
        var reply = new McpReplyCodec(4096, 8).read(events, McpRequestId.of(5), new PingResponder(session), sent::add);
        equal(McpRequestId.of(5), reply.id());
        equal(1, sent.size());
        equal("{\"jsonrpc\":\"2.0\",\"id\":\"p-1\",\"result\":{}}", sent.get(0).body());
        equal("2025-06-18", sent.get(0).headers().get("MCP-Protocol-Version"));
        equal("sid-1", sent.get(0).headers().get("MCP-Session-Id"));
        equal("application/json", sent.get(0).headers().get("Content-Type"));
    }

    private static void stringAndNumericPingIdsArePreserved() {
        var session = new McpFlow.Session(McpRequests.MODERN_VERSION, null, null, "client", "1");
        List<McpRequests.Request> sent = new ArrayList<>();
        var events = finite(String.format(PING, "42"), String.format(PING, "\"forty-two\""), String.format(REPLY, "\"call\""));
        new McpReplyCodec(4096, 8).read(events, McpRequestId.of("call"), new PingResponder(session), sent::add);
        equal("{\"jsonrpc\":\"2.0\",\"id\":42,\"result\":{}}", sent.get(0).body());
        equal("{\"jsonrpc\":\"2.0\",\"id\":\"forty-two\",\"result\":{}}", sent.get(1).body());
        equal(McpRequests.MODERN_VERSION, sent.get(0).headers().get("MCP-Protocol-Version"));
        equal(null, sent.get(0).headers().get("MCP-Session-Id"));
    }

    private static void replyReturnsWithoutWaitingForStreamEnd() {
        var events = finite(String.format(PING, "1"), String.format(REPLY, "7"));
        List<McpRequests.Request> sent = new ArrayList<>();
        var session = new McpFlow.Session("2025-06-18", null, null, "client", "1");
        equal(McpRequestId.of(7), new McpReplyCodec(4096, 8)
            .read(events, McpRequestId.of(7), new PingResponder(session), sent::add).id());
        equal(1, sent.size());
    }

    private static void disabledHandlerSendsNothing() {
        List<McpRequests.Request> sent = new ArrayList<>();
        var events = finite(String.format(PING, "1"), String.format(REPLY, "7"));
        equal(McpRequestId.of(7), new McpReplyCodec(4096, 8)
            .read(events, McpRequestId.of(7), ServerRequestHandler.disabled(), sent::add).id());
        equal(0, sent.size());
    }

    private static void eventCapsStillApply() {
        var session = new McpFlow.Session("2025-06-18", null, null, "client", "1");
        expectFailure("SSE reply event limit", () -> new McpReplyCodec(4096, 2).read(
            finite(String.format(PING, "1"), String.format(PING, "2"), String.format(REPLY, "7")),
            McpRequestId.of(7), new PingResponder(session), request -> {}));
        expectFailure("SSE event exceeds limit", () -> new McpReplyCodec(40, 8).read(
            finite(String.format(REPLY, "7")), McpRequestId.of(7), new PingResponder(session), request -> {}));
        expectFailure("SSE closed before reply", () -> new McpReplyCodec(4096, 8).read(
            new ArrayDeque<>(List.of(String.format(PING, "1")))::poll, McpRequestId.of(7), new PingResponder(session), request -> {}));
    }

    private static void inputStreamReaderStopsAtMatchingReply() {
        String wire = String.format(PING, "\"r\"").replace("\n", "\r\n") + String.format(REPLY, "3");
        var stream = new UnendingStream(wire);
        List<McpRequests.Request> sent = new ArrayList<>();
        var session = new McpFlow.Session("2025-03-26", "s", null, "client", "1");
        equal(McpRequestId.of(3), new McpReplyCodec(4096, 8)
            .read(new SseEventReader(stream, 4096), McpRequestId.of(3), new PingResponder(session), sent::add).id());
        equal("{\"jsonrpc\":\"2.0\",\"id\":\"r\",\"result\":{}}", sent.get(0).body());
        var reader = new SseEventReader(new java.io.ByteArrayInputStream("data: x\n\n".getBytes()), 4096);
        equal("data: x\n\n", reader.nextEvent());
        equal(null, reader.nextEvent());
        expectFailure("incomplete SSE response", () -> new SseEventReader(
            new java.io.ByteArrayInputStream("data: x\n".getBytes()), 4096).nextEvent());
    }

    private static void inputStreamReaderEnforcesByteCap() {
        String ping = String.format(PING, "1");
        var longLived = new SseEventReader(new java.io.ByteArrayInputStream(ping.repeat(50).getBytes()), ping.length());
        for (int event = 0; event < 50; event++) equal(ping, longLived.nextEvent());
        String oversized = "data: " + "x".repeat(ping.length()) + "\n\n";
        var reader = new SseEventReader(new java.io.ByteArrayInputStream(oversized.getBytes()), ping.length());
        expectFailure("SSE event exceeds limit", reader::nextEvent);
    }

    private static void completeEventStreamResponseAnswersPings() {
        var session = new McpFlow.Session("2025-06-18", "sid", null, "client", "1");
        List<McpRequests.Request> sent = new ArrayList<>();
        var response = new McpFlow.HttpResponse(200, Map.of("Content-Type", "text/event-stream"),
            String.format(PING, "1") + String.format(REPLY, "2"), true);
        equal(McpRequestId.of(2), new McpReplyCodec(4096, 8)
            .parse(response, McpRequestId.of(2), new PingResponder(session), sent::add).id());
        equal(1, sent.size());
    }

    private static void legacySseCallAnswersPing() {
        var events = new ArrayDeque<>(List.of(String.format(PING, "\"lp\""), NOTIFICATION, SAMPLING, String.format(REPLY, "4")));
        List<String> posts = new ArrayList<>();
        var port = ssePort(events, posts);
        var session = new McpFlow.Session(McpRequests.SSE_VERSION, null, "/message", "client", "1");
        var reply = new McpFlow(4096, 8).callSse(port, session, session.request("tools/list", null, 4),
            McpRequestId.of(4), new PingResponder(session));
        equal(McpRequestId.of(4), reply.id());
        equal(2, posts.size());
        equal("/message {\"jsonrpc\":\"2.0\",\"id\":\"lp\",\"result\":{}} 2024-11-05", posts.get(1));
    }

    private static void legacySseOpenAnswersPingBeforeInitializeReply() {
        var events = new ArrayDeque<>(List.of("event: endpoint\ndata: /message\n\n", String.format(PING, "8"),
            "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}\n\n"));
        List<String> posts = new ArrayList<>();
        var session = new McpFlow(4096, 8).openSse(ssePort(events, posts), "https://example.test/sse",
            ClientProfile.of("client", "1"), PingResponder::new);
        equal("/message", session.endpoint());
        equal("/message {\"jsonrpc\":\"2.0\",\"id\":8,\"result\":{}} 2024-11-05", posts.get(1));
        equal(3, posts.size());
    }

    private static McpFlow.SsePort ssePort(ArrayDeque<String> events, List<String> posts) {
        return new McpFlow.SsePort() {
            public String nextEvent() { return events.poll(); }
            public McpFlow.HttpResponse post(String endpoint, McpRequests.Request request) {
                posts.add(endpoint + " " + request.body() + " " + request.headers().get("MCP-Protocol-Version"));
                return new McpFlow.HttpResponse(202, Map.of(), "", true);
            }
        };
    }

    private static McpEventSource finite(String... events) {
        var remaining = new ArrayDeque<>(List.of(events));
        return () -> {
            if (remaining.isEmpty()) throw new AssertionError("read past the matching reply");
            return remaining.poll();
        };
    }

    private static final class UnendingStream extends java.io.InputStream {
        private final java.io.ByteArrayInputStream wire;

        UnendingStream(String wire) { this.wire = new java.io.ByteArrayInputStream(wire.getBytes()); }

        public int read() {
            int next = wire.read();
            if (next < 0) throw new AssertionError("read past the matching reply");
            return next;
        }

        public int read(byte[] buffer, int offset, int length) {
            if (wire.available() == 0) throw new AssertionError("read past the matching reply");
            return wire.read(buffer, offset, Math.min(length, wire.available()));
        }
    }

    private static void equal(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + " got " + actual);
    }

    private static void expectFailure(String expectedMessagePart, Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) {
            if (expected.getMessage() == null || !expected.getMessage().contains(expectedMessagePart))
                throw new AssertionError("Expected '" + expectedMessagePart + "' got '" + expected.getMessage() + "'");
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException containing '" + expectedMessagePart + "'");
    }
}
