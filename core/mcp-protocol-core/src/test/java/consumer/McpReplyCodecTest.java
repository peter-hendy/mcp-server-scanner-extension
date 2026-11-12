package consumer;

import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpReplyCodec;
import com.mcpscanner.core.protocol.McpResponses;
import java.math.BigDecimal;
import java.util.Map;

public final class McpReplyCodecTest {
    public static void main(String[] args) {
        genericIds();
        maximumLengthUnicodeIdsSerialize();
        streamedPingIsAnsweredThroughPublicApi();
        McpReplyCodec codec = new McpReplyCodec(4096, 4);
        String body = "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"nested\":[null,1.250,true,false,{\"text\":\"quote\\\" slash\\\\ newline\\n π\"}]}}";
        McpResponses.Reply reply = codec.parse(new McpFlow.HttpResponse(200, Map.of(), body, true), 7);
        McpResponses.Reply roundTrip = McpResponses.parse(codec.json(reply), 4096);
        if (!roundTrip.equals(reply)) throw new AssertionError("reply changed after JSON round trip");
        var nested = (java.util.List<?>) roundTrip.result().get("nested");
        if (nested.get(0) != null || !new BigDecimal("1.250").equals(nested.get(1)))
            throw new AssertionError("null or exact decimal lost");
        String tiny = "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"tiny\":0.00100,\"exponent\":1e001}}";
        McpResponses.Reply decimal = codec.parse(response(tiny, false), 7);
        if (!decimal.equals(McpResponses.parse(codec.json(decimal), 4096)))
            throw new AssertionError("fraction or exponent changed");
        String error = "{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-32602,\"message\":\"invalid\",\"data\":null}}";
        McpResponses.Reply rejected = codec.parse(response(error, false), 7);
        if (!rejected.equals(McpResponses.parse(codec.json(rejected), 4096)))
            throw new AssertionError("RPC error changed");
        String event = "event: message\ndata: " + body + "\n\n";
        String notification = "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n";
        if (!codec.parse(response(notification + event, true), 7).equals(reply))
            throw new AssertionError("SSE reply changed");
        fails(() -> codec.parse(response(body, false), 8));
        fails(() -> codec.parse(response(event, true), 8));
        fails(() -> codec.parse(response(event.substring(0, event.length() - 1), true), 7));
        fails(() -> codec.parse(response("data: {broken}\n\n", true), 7));
        fails(() -> codec.parse(new McpFlow.HttpResponse(200, Map.of(), body, false), 7));
        fails(() -> new McpReplyCodec(20, 4).parse(response(body, false), 7));
        fails(() -> new McpReplyCodec(4096, 1).parse(response(notification + event, true), 7));
        fails(() -> new McpReplyCodec(20, 4).json(reply));
        fails(() -> codec.json(null));
        fails(() -> codec.json(new McpResponses.Reply(7, null, null)));
        fails(() -> codec.json(new McpResponses.Reply(7, Map.of(), Map.of())));
        System.out.println("McpReplyCodecTest passed");
    }

    private static void genericIds() {
        var codec = new McpReplyCodec(65536, 4);
        if (!com.mcpscanner.core.protocol.McpRequestId.of(7).value().equals(7L))
            throw new AssertionError("Java integer literal ID changed");
        String escapedBoundary = "\"" + "\\ud83d\\ude00".repeat(4096) + "\"";
        if (!com.mcpscanner.core.protocol.McpRequestId.parse(escapedBoundary).value().equals("😀".repeat(4096)))
            throw new AssertionError("Unicode ID boundary changed");
        for (Object value : java.util.List.of("scan-\"\\\nπ", "", Long.MIN_VALUE, -1L, Long.MAX_VALUE)) {
            var id = com.mcpscanner.core.protocol.McpRequestId.of(value);
            for (String version : java.util.List.of("2026-07-28", "2025-03-26", "2025-06-18", "2025-11-25", "2024-11-05")) {
                var session = new McpFlow.Session(version, null, version.equals("2024-11-05") ? "/message" : null, "test", "1");
                String body = session.operation("tools/list", "{}", id).body();
                var request = (java.util.Map<?, ?>) com.mcpscanner.core.protocol.JsonDocuments.parse(body, 65536);
                if (!id.equals(com.mcpscanner.core.protocol.McpRequestId.of(request.get("id"))))
                    throw new AssertionError("request ID changed");
            }
            String reply = "{\"jsonrpc\":\"2.0\",\"id\":" + id.json() + ",\"result\":{}}";
            for (boolean sse : new boolean[] {false, true}) {
                var parsed = codec.parse(response(sse ? "data: " + reply + "\n\n" : reply, sse), id);
                if (!parsed.id().equals(id) || !codec.jsonGeneric(parsed).contains("\"id\":" + id.json()))
                    throw new AssertionError("response ID changed");
                fails(() -> codec.parse(response(sse ? "data: " + reply + "\n\n" : reply, sse),
                    com.mcpscanner.core.protocol.McpRequestId.of("different")));
            }
        }
        for (Object invalid : java.util.List.of(true, 1.5, new java.math.BigDecimal("9223372036854775808"), "x".repeat(4097), "\ud800"))
            fails(() -> com.mcpscanner.core.protocol.McpRequestId.of(invalid));
        fails(() -> com.mcpscanner.core.protocol.McpRequestId.of(null));
        for (String invalid : java.util.List.of("null", "true", "1.5", "9223372036854775808", "{}", "[]"))
            fails(() -> com.mcpscanner.core.protocol.McpRequestId.parse(invalid));
    }

    private static void maximumLengthUnicodeIdsSerialize() {
        for (String scalar : java.util.List.of("😀", "\u0000", "\uffff", "\\", "\"", "a")) {
            var id = com.mcpscanner.core.protocol.McpRequestId.of(scalar.repeat(4096));
            String json = id.json();
            if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4096 * 12 + 2)
                throw new AssertionError("ID exceeded the maximum escaped scalar size");
            if (!id.equals(com.mcpscanner.core.protocol.McpRequestId.parse(json)))
                throw new AssertionError("Maximum length Unicode ID changed after serialization");
            for (String version : java.util.List.of("2026-07-28", "2025-03-26", "2025-06-18", "2025-11-25", "2024-11-05")) {
                var session = new McpFlow.Session(version, null, version.equals("2024-11-05") ? "/message" : null, "test", "1");
                var request = (java.util.Map<?, ?>) com.mcpscanner.core.protocol.JsonDocuments.parse(
                    session.operation("tools/list", "{}", id).body(), 65536);
                if (!id.equals(com.mcpscanner.core.protocol.McpRequestId.of(request.get("id"))))
                    throw new AssertionError("Maximum length Unicode request ID changed");
            }
        }
    }

    private static void streamedPingIsAnsweredThroughPublicApi() {
        var session = new McpFlow.Session("2025-06-18", "sid", null, "test", "1");
        String wire = "data: {\"jsonrpc\":\"2.0\",\"id\":\"p\",\"method\":\"ping\"}\n\n"
            + "data: {\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{}}\n\n";
        var sent = new java.util.ArrayList<com.mcpscanner.core.protocol.McpRequests.Request>();
        var events = new com.mcpscanner.core.protocol.SseEventReader(
            new java.io.ByteArrayInputStream(wire.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 4096);
        var reply = new McpReplyCodec(4096, 4).read(events, com.mcpscanner.core.protocol.McpRequestId.of(3),
            new com.mcpscanner.core.protocol.PingResponder(session), sent::add);
        if (!reply.id().value().equals(3L) || sent.size() != 1
            || !sent.get(0).body().equals("{\"jsonrpc\":\"2.0\",\"id\":\"p\",\"result\":{}}")
            || !"sid".equals(sent.get(0).headers().get("MCP-Session-Id")))
            throw new AssertionError("streamed ping not answered");
    }

    private static McpFlow.HttpResponse response(String body, boolean sse) {
        return new McpFlow.HttpResponse(200, sse ? Map.of("cOnTeNt-TyPe", "text/event-stream; charset=utf-8") : Map.of(), body, true);
    }

    private static void fails(Runnable operation) {
        try { operation.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("invalid reply accepted");
    }
}
