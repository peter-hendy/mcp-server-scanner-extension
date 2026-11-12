package com.mcpscanner.core.protocol;

import java.util.Map;

public final class ProtocolCoreTest {
    public static void main(String[] args) {
        clientProfile();
        ServerPingTests.run();
        var modern = McpRequests.modern("server/discover", "scancheck", "1", null);
        equal("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}",
            McpRequests.envelope("tools/list", "{}"));
        if (!McpRequests.envelope("tools/list", "{}", 7).contains("\"id\":7")) throw new AssertionError("explicit ID missing");
        equal("{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\",\"io.modelcontextprotocol/clientInfo\":{\"name\":\"scancheck\",\"version\":\"1\"},\"io.modelcontextprotocol/clientCapabilities\":{}},\"cursor\":\"next\"}",
            McpRequests.modernParams("scancheck", "1", "next"));
        equal("{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"scancheck\",\"version\":\"1\"}}",
            McpRequests.initializeParams("2025-06-18", "scancheck", "1"));
        equal("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\",\"io.modelcontextprotocol/clientInfo\":{\"name\":\"scancheck\",\"version\":\"1\"},\"io.modelcontextprotocol/clientCapabilities\":{}}}}", modern.body());
        equal("server/discover", modern.headers().get("Mcp-Method"));
        equal("list_files", McpRequests.modernOperation("tools/call", "scancheck", "1", "{\"name\":\"list_files\",\"arguments\":{}}")
            .headers().get("Mcp-Name"));
        equal("file:///tmp/example", McpRequests.modernOperation("resources/read", "scancheck", "1", "{\"uri\":\"file:///tmp/example\"}")
            .headers().get("Mcp-Name"));
        equal("=?base64?" + java.util.Base64.getEncoder().encodeToString("name\nother".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "?=",
            McpRequests.modernOperation("prompts/get", "scancheck", "1", "{\"name\":\"name\\nother\"}").headers().get("Mcp-Name"));
        expectFailure("operation name or URI required", () -> McpRequests.modernOperation("tools/call", "scancheck", "1", "{\"arguments\":{}}"));
        equal("2026-07-28", modern.headers().get("MCP-Protocol-Version"));
        equal("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\",\"io.modelcontextprotocol/clientInfo\":{\"name\":\"scancheck\",\"version\":\"1\"},\"io.modelcontextprotocol/clientCapabilities\":{}},\"cursor\":\"next\"}}", McpRequests.modern("tools/list", "scancheck", "1", "next").body());
        equal("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"scancheck\",\"version\":\"1\"}}}", McpRequests.initialize("2025-06-18", "scancheck", "1").body());
        equal("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", McpRequests.initialized("2025-06-18", "s1").body());
        if (!McpRequests.initialize("2024-11-05", "scancheck", "1").body().contains("\"protocolVersion\":\"2024-11-05\""))
            throw new AssertionError("2024 SSE initialize version missing");
        equal("s1", McpRequests.initialized("2025-06-18", "s1").headers().get("MCP-Session-Id"));
        equal(java.util.List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05"),
            McpRequests.supportedInitializeVersions());
        var reply = McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-03-26\"}}", 1024);
        equal("2025-03-26", McpResponses.negotiate(reply, "2025-06-18", Map.of("Mcp-Session-Id", "sid")).version());
        equal("sid", McpResponses.negotiate(reply, "2025-06-18", Map.of("Mcp-Session-Id", "sid")).sessionId());
        expectFailure("invalid session ID", () -> McpResponses.negotiate(reply, "2025-06-18", Map.of("Mcp-Session-Id", "bad\nheader")));
        var oldOverPost = McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}", 1024);
        equal("2024-11-05", McpResponses.negotiate(oldOverPost, "2025-06-18", Map.of("Mcp-Session-Id", "sid")).version());
        equal(null, McpResponses.negotiate(oldOverPost, "2025-06-18", Map.of()).sessionId());
        expectFailure("object key required", () -> McpResponses.parse("{\"jsonrpc\":\"2.0\",", 1024));
        expectFailure("exactly one non-null result or error", () -> McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1}", 1024));
        expectFailure("JSON exceeds limit", () -> McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}", 10));
        equal(null, McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"optional\":null}}", 1024).result().get("optional"));
        var offer = McpResponses.counterOffer("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"data\":{\"supportedVersions\":[\"2025-11-25\"]}}}", 1024);
        equal("2025-11-25", offer);
        equal("2025-03-26", McpResponses.counterOffer("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"data\":{\"supported\":[\"2099-01-01\",\"2025-03-26\"]}}}", 1024));
        equal("2025-03-26", McpResponses.counterOffer("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"Unsupported protocol version: 2025-06-18\"}}", 1024));
        equal("2024-11-05", McpResponses.counterOffer("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"data\":{\"supported\":[\"2024-11-05\"]}}}", 1024));
        equal("2025-11-25", McpResponses.counterOffer("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"data\":{\"supported\":[\"2024-11-05\",\"2025-03-26\",\"2025-11-25\"]}}}", 1024));
        equal(true, McpResponses.modernDiscovery(McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}", 1024)));
        equal(false, McpResponses.modernDiscovery(McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"supportedVersions\":[\"2026-07-28\"]}}", 1024)));
        equal(false, McpResponses.modernDiscovery(McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":[],\"supportedVersions\":[\"2026-07-28\"]}}", 1024)));
        equal(null, McpResponses.counterOffer("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"data\":{\"supportedVersions\":[\"2099-01-01\"]}}}", 1024));
        equal("/message?session=x", McpSse.endpoint("https://example.test/sse", "event: endpoint\ndata: /message?session=x\n\n", 1024));
        expectFailure("cross-origin endpoint", () -> McpSse.endpoint("https://example.test/sse", "event: endpoint\ndata: https://evil.test/message\n\n", 1024));
        equal(null, McpSse.reply("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}\n\n", 1, 1024));
        equal(null, McpSse.reply("event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n", 1, 1024));
        equal(null, McpSse.reply(": keepalive\n\n", 1, 1024));
        equal(null, McpSse.reply("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"ping\"}\n\n", 1, 1024));
        equal(1L, McpSse.reply("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n\n", 1, 1024).id());
        expectFailure("incomplete SSE event", () -> McpSse.reply("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}", 1, 1024));
        nullErrorIdReplies();
        sseMixedLineEndings();
        sseIncompleteEvents();
        notificationFraming();
        flowCases();
        directInitializeAdmissionUsesPreferredVersion();
        httpInitializeOffersNewestHandshakeVersion();
        nullBranchesAreAbsent();
        descriptiveHandshakeErrors();
        mozillaRecordedStatelessSession();
        httpCounterOfferTo2024();
        sseTransportNegotiatesNewerVersion();
        httpNotificationOn2024KeepsSessionId();
        System.out.println("ProtocolCoreTest passed");
    }

    private static void nullErrorIdReplies() {
        var nullId = McpResponses.parseGenericAllowingNullErrorId(
            "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"bad\"}}", 1024);
        equal(null, nullId.id());
        equal(null, nullId.result());
        equal("bad", nullId.error().get("message"));
        equal(1L, McpResponses.parseGenericAllowingNullErrorId(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}", 1024).id().value());
        expectFailure("bounded string or signed 64-bit integer ID required", () -> McpResponses.parseGeneric("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{}}", 1024));
        expectFailure("bounded string or signed 64-bit integer ID required", () -> McpResponses.parseGenericAllowingNullErrorId("{\"jsonrpc\":\"2.0\",\"id\":null,\"result\":{}}", 1024));
        expectFailure("bounded string or signed 64-bit integer ID required", () -> McpResponses.parseGenericAllowingNullErrorId("{\"jsonrpc\":\"2.0\",\"error\":{}}", 1024));
        expectFailure("bounded string or signed 64-bit integer ID required", () -> McpResponses.parseGenericAllowingNullErrorId("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":\"x\"}", 1024));
        expectFailure("invalid JSON-RPC version", () -> McpResponses.parseGenericAllowingNullErrorId("{\"jsonrpc\":\"1.0\",\"id\":null,\"error\":{}}", 1024));
        equal("{\"a\":1}\n{\"b\":2}", McpSse.data("event: other\ndata: {\"a\":1}\ndata: {\"b\":2}\n\n", 1024));
        equal("", McpSse.data(": keepalive\n\n", 1024));
        expectFailure("incomplete SSE event", () -> McpSse.data("data: {\"a\":1}", 1024));
    }

    private static void sseMixedLineEndings() {
        for (String terminator : java.util.List.of("\n\r", "\n\r\n", "\r\n\n", "\r\n\r", "\r\r\n", "\n\n", "\r\r", "\r\n\r\n")) {
            equal("/messages?session=mixed", McpSse.endpoint("https://example.test/sse",
                "event: endpoint\r\ndata: /messages?session=mixed" + terminator, 1024));
            equal(1L, McpSse.reply("event: message\rdata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"
                + terminator, 1, 1024).id());
        }
    }

    private static void sseIncompleteEvents() {
        for (String terminator : java.util.List.of("", "\r", "\n", "\r\n")) {
            expectFailure("incomplete SSE event", () -> McpSse.endpoint("https://example.test/sse",
                "event: endpoint\r\ndata: /messages" + terminator, 1024));
            expectFailure("incomplete SSE event", () -> McpSse.reply("event: message\rdata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"
                + terminator, 1, 1024));
        }
    }

    private static void notificationFraming() {
        for (String version : java.util.List.of(McpRequests.MODERN_VERSION, "2025-03-26", "2025-06-18", "2025-11-25", McpRequests.SSE_VERSION)) {
            var session = new McpFlow.Session(version, version.equals(McpRequests.SSE_VERSION) ? null : "session", version.equals(McpRequests.SSE_VERSION) ? "/messages" : null, "client", "1");
            var request = session.notification("notifications/cancelled", "{\"requestId\":\"job\"}");
            var document = (Map<?, ?>) JsonDocuments.parse(request.body(), 65536);
            equal(false, document.containsKey("id"));
            equal("notifications/cancelled", document.get("method"));
            var params = (Map<?, ?>) document.get("params");
            equal("job", params.get("requestId"));
            equal(version, request.headers().get("MCP-Protocol-Version"));
            equal(version.equals(McpRequests.MODERN_VERSION), params.containsKey("_meta"));
            if (version.equals(McpRequests.MODERN_VERSION)) equal("notifications/cancelled", request.headers().get("Mcp-Method"));
            else equal(version.equals(McpRequests.SSE_VERSION) ? null : "session", request.headers().get("MCP-Session-Id"));
        }
    }

    private static void httpInitializeOffersNewestHandshakeVersion() {
        for (String answered : java.util.List.of("2025-11-25", "2025-06-18")) {
            var offered = new java.util.ArrayList<String>();
            var session = new McpFlow(2048, 4).openHttp(request -> {
                if (request.body().contains("server/discover")) return new McpFlow.HttpResponse(404, Map.of(), "", true);
                if (!request.body().contains("\"initialize\"")) return new McpFlow.HttpResponse(202, Map.of(), "", true);
                offered.add(request.headers().get("MCP-Protocol-Version"));
                return new McpFlow.HttpResponse(200, Map.of("MCP-Session-Id", "sid"),
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"" + answered + "\"}}", true);
            }, "client", "1");
            equal(java.util.List.of("2025-11-25"), offered);
            equal(answered, session.version());
        }
    }

    private static void directInitializeAdmissionUsesPreferredVersion() {
        for (String version : java.util.List.of(McpRequests.SSE_VERSION, "2025-03-26", "2025-06-18", "2025-11-25")) {
            var sends = new java.util.concurrent.atomic.AtomicInteger();
            var flow = new McpFlow(2048, 4);
            var session = flow.openInitializeHttp(request -> {
                sends.incrementAndGet();
                if (request.body().contains("server/discover")) throw new AssertionError("Unexpected modern probe");
                equal(version, request.headers().get("MCP-Protocol-Version"));
                return request.body().contains("\"initialize\"")
                    ? new McpFlow.HttpResponse(200, Map.of("MCP-Session-Id", "new-session"),
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"" + version + "\"}}", true)
                    : new McpFlow.HttpResponse(202, Map.of(), "", true);
            }, "client", "1", version);
            equal(version, session.version());
            equal("new-session", session.sessionId());
            equal(2, sends.get());
        }
    }

    private static void flowCases() {
        McpFlow flow = new McpFlow(2048, 4);
        var modern = flow.openHttp(request -> new McpFlow.HttpResponse(200, Map.of(),
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}", true), "client", "1");
        equal(McpRequests.MODERN_VERSION, flow.openModernHttp(request -> new McpFlow.HttpResponse(200, Map.of(),
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}", true),
            "client", "1").version());
        expectFailure("server/discover failed", () -> flow.openModernHttp(request -> new McpFlow.HttpResponse(404, Map.of(), "", true),
            "client", "1"));
        java.util.concurrent.atomic.AtomicInteger modernRetries = new java.util.concurrent.atomic.AtomicInteger();
        equal(McpRequests.MODERN_VERSION, flow.openHttp(request -> {
            int call = modernRetries.incrementAndGet();
            return new McpFlow.HttpResponse(call == 1 ? 400 : 200, Map.of(), call == 1
                ? "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32022,\"data\":{\"supportedVersions\":[\"2026-07-28\"]}}}"
                : "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}", true);
        }, "client", "1").version());
        equal(2, modernRetries.get());
        equal(McpRequests.MODERN_VERSION, flow.openHttp(request -> new McpFlow.HttpResponse(200,
            Map.of("Content-Type", "text/event-stream"),
            "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n"
                + "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}\n\n", true),
            "client", "1").version());
        equal(McpRequests.MODERN_VERSION, modern.version());
        if (!modern.operation("tools/call", "{\"name\":\"x\",\"arguments\":{}}").body().contains("\"arguments\":{}"))
            throw new AssertionError("modern operation parameters lost");
        java.util.List<String> legacyMethods = new java.util.ArrayList<>();
        var legacy = flow.openHttp(request -> {
            legacyMethods.add(request.body());
            if (request.body().contains("server/discover")) return new McpFlow.HttpResponse(404, Map.of(), "", true);
            if (request.body().contains("notifications/initialized")) return new McpFlow.HttpResponse(202, Map.of(), "", true);
            if (request.body().contains("2025-11-25")) return new McpFlow.HttpResponse(400, Map.of(),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"data\":{\"supportedVersions\":[\"2025-06-18\"]}}}", true);
            return new McpFlow.HttpResponse(200, Map.of("MCP-Session-ID", "session"),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}", true);
        }, "client", "1");
        equal("2025-06-18", legacy.version());
        var nullIdFallback = flow.openHttp(request -> {
            if (request.body().contains("server/discover"))
                return new McpFlow.HttpResponse(404, Map.of(), "", true);
            if (request.body().contains("notifications/initialized"))
                return new McpFlow.HttpResponse(202, Map.of(), "", true);
            if (request.body().contains("2025-11-25"))
                return new McpFlow.HttpResponse(400, Map.of(),
                    "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"Unsupported protocol version: 2025-11-25\"}}", true);
            return new McpFlow.HttpResponse(200, Map.of("MCP-Session-Id", "fallback-session"),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-03-26\"}}", true);
        }, "client", "1");
        equal("2025-03-26", nullIdFallback.version());
        var initializeSends = new int[1];
        var walkedDown = flow.openHttp(request -> {
            if (request.body().contains("server/discover"))
                return new McpFlow.HttpResponse(404, Map.of(), "", true);
            if (request.body().contains("notifications/initialized"))
                return new McpFlow.HttpResponse(202, Map.of(), "", true);
            initializeSends[0]++;
            if (!request.body().contains("2024-11-05")) {
                String offered = request.body().replaceAll(".*\"protocolVersion\":\"([^\"]+)\".*", "$1");
                return new McpFlow.HttpResponse(400, Map.of(),
                    "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32600,\"message\":\"Unsupported protocol version: " + offered + "\"}}", true);
            }
            return new McpFlow.HttpResponse(200, Map.of(),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}", true);
        }, "client", "1");
        equal("2024-11-05", walkedDown.version());
        equal(4, initializeSends[0]);
        expectFailure("returned a JSON-RPC error", () -> McpResponses.negotiate(new McpResponses.Reply(1, null, null), "2025-06-18", Map.of()));
        equal("session", legacy.request("tools/list", "quoted\"cursor").headers().get("MCP-Session-Id"));
        var oldPost = flow.openHttp(request -> {
            if (request.body().contains("server/discover"))
                return new McpFlow.HttpResponse(404, Map.of(), "", true);
            if (request.body().contains("notifications/initialized"))
                return new McpFlow.HttpResponse(202, Map.of(), "", true);
            return new McpFlow.HttpResponse(200, Map.of("Mcp-Session-Id", "old-post-session"),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}", true);
        }, "client", "1");
        equal("2024-11-05", oldPost.version());
        equal("old-post-session", oldPost.request("tools/list", null).headers().get("MCP-Session-Id"));
        if (!legacy.request("tools/list", null, 9).body().contains("\"id\":9")) throw new AssertionError("catalog ID missing");
        if (!legacy.request("tools/list", "quoted\"cursor").body().contains("quoted\\\"cursor")) throw new AssertionError("cursor not escaped");
        equal(4, legacyMethods.size());
        equal("2025-06-18", flow.openHttp(request -> {
            if (request.body().contains("server/discover")) return new McpFlow.HttpResponse(404, Map.of(), "", true);
            if (request.body().contains("notifications/initialized")) return new McpFlow.HttpResponse(202, Map.of(), "", true);
            return new McpFlow.HttpResponse(200, Map.of("Content-Type", "text/event-stream", "Mcp-Session-Id", "sse-id"),
                "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}\n\n", true);
        }, "client", "1").version());
        expectFailure("incomplete SSE response", () -> flow.openHttp(request -> {
            if (request.body().contains("server/discover")) return new McpFlow.HttpResponse(404, Map.of(), "", true);
            return new McpFlow.HttpResponse(200, Map.of("Content-Type", "text/event-stream"),
                "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}", true);
        }, "client", "1"));
        expectFailure("notifications/initialized for protocolVersion 2025-06-18 failed: HTTP 202 (incomplete)", () -> flow.openHttp(request -> {
            if (request.body().contains("server/discover")) return new McpFlow.HttpResponse(404, Map.of(), "", true);
            if (request.body().contains("notifications/initialized")) return new McpFlow.HttpResponse(202, Map.of(), "", false);
            return new McpFlow.HttpResponse(200, Map.of(),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}", true);
        }, "client", "1"));
        expectFailure("initialize offering protocolVersion 2025-11-25 failed: HTTP 200 (incomplete)", () -> flow.openHttp(request -> new McpFlow.HttpResponse(200, Map.of(),
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"supportedVersions\":[\"2026-07-28\"]}}", false), "client", "1"));

        java.util.ArrayDeque<String> events = new java.util.ArrayDeque<>();
        events.add("event: endpoint\ndata: /message\n\n");
        events.add("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}\n\n");
        events.add("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}\n\n");
        events.add("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{}}\n\n");
        events.add("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}\n\n");
        McpFlow.SsePort port = new McpFlow.SsePort() {
            public String nextEvent() { return events.poll(); }
            public McpFlow.HttpResponse post(String endpoint, McpRequests.Request request) {
                equal("/message", endpoint);
                return new McpFlow.HttpResponse(202, Map.of(), "", true);
            }
        };
        var sse = flow.openSse(port, "https://example.test/sse", "client", "1");
        equal(McpRequests.SSE_VERSION, sse.version());
        equal(1L, flow.callSse(port, sse, sse.request("tools/list", null), 1).id());
    }

    private static void nullBranchesAreAbsent() {
        var withNullError = McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"},\"error\":null}", 1024);
        equal("2025-06-18", withNullError.result().get("protocolVersion"));
        equal(null, withNullError.error());
        var withNullResult = McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null,\"error\":{\"code\":-32601}}", 1024);
        equal(null, withNullResult.result());
        equal(true, withNullResult.error().containsKey("code"));
        expectFailure("exactly one non-null result or error", () ->
            McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null,\"error\":null}", 1024));
        equal(null, McpResponses.parseGenericAllowingNullErrorId(
            "{\"jsonrpc\":\"2.0\",\"id\":null,\"result\":null,\"error\":{\"code\":-32600}}", 1024).id());
    }

    private static void descriptiveHandshakeErrors() {
        var future = McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2099-01-01\"}}", 1024);
        expectFailure("server answered protocolVersion 2099-01-01 to offered 2025-06-18", () ->
            McpResponses.negotiate(future, "2025-06-18", Map.of()));
        var missing = McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}", 1024);
        expectFailure("server answered protocolVersion null to offered 2025-06-18", () ->
            McpResponses.negotiate(missing, "2025-06-18", Map.of()));
        var rejected = McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"message\":\"nope\"}}", 1024);
        expectFailure("initialize offering protocolVersion 2025-06-18 returned a JSON-RPC error: nope", () ->
            McpResponses.negotiate(rejected, "2025-06-18", Map.of()));
        expectFailure("offered protocolVersion 2026-07-28 is not a supported initialize version", () ->
            McpResponses.negotiate(future, McpRequests.MODERN_VERSION, Map.of()));
        expectFailure("initialize offering protocolVersion 2025-06-18 failed: HTTP 500", () ->
            new McpFlow(2048, 4).openInitializeHttp(request -> new McpFlow.HttpResponse(500, Map.of(), "", true),
                "client", "1", "2025-06-18"));
        expectFailure("initialize offering protocolVersion 2025-06-18 failed: no response", () ->
            new McpFlow(2048, 4).openInitializeHttp(request -> null, "client", "1", "2025-06-18"));
        expectFailure("preferred protocolVersion 2026-07-28 is not a supported initialize version", () ->
            new McpFlow(2048, 4).openInitializeHttp(request -> null, "client", "1", McpRequests.MODERN_VERSION));
        var endpointless = new McpFlow.Session(McpRequests.SSE_VERSION, null, null, "client", "1");
        expectFailure("SSE endpoint", () -> new McpFlow(2048, 4).callSse(new EmptySsePort(),
            endpointless, endpointless.request("tools/list", null), 1));
    }

    private static void mozillaRecordedStatelessSession() {
        java.util.List<String> methods = new java.util.ArrayList<>();
        var session = new McpFlow(65536, 8).openHttp(request -> {
            var method = (String) ((Map<?, ?>) JsonDocuments.parse(request.body(), 65536)).get("method");
            methods.add(method);
            return switch (method) {
                case "server/discover" -> new McpFlow.HttpResponse(200, Map.of("Content-Type", "application/json"),
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found: server/discover\"}}", true);
                case "initialize" -> {
                    equal(McpRequests.HANDSHAKE_VERSION, request.headers().get("MCP-Protocol-Version"));
                    yield new McpFlow.HttpResponse(200, Map.of("Content-Type", "application/json"),
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"serverInfo\":{\"name\":\"glean-dictionary\",\"version\":\"1.0.0\"},\"capabilities\":{\"tools\":{}}}}", true);
                }
                case "notifications/initialized" -> {
                    equal("2024-11-05", request.headers().get("MCP-Protocol-Version"));
                    equal(null, request.headers().get("MCP-Session-Id"));
                    yield new McpFlow.HttpResponse(200, Map.of("Content-Type", "application/json"), "{\"jsonrpc\":\"2.0\"}", true);
                }
                default -> throw new AssertionError("Unexpected method " + method);
            };
        }, "client", "1");
        equal(java.util.List.of("server/discover", "initialize", "notifications/initialized"), methods);
        equal("2024-11-05", session.version());
        equal(null, session.sessionId());
        equal(null, session.endpoint());
        var list = session.request("tools/list", null, 2);
        equal("2024-11-05", list.headers().get("MCP-Protocol-Version"));
        equal(null, list.headers().get("MCP-Session-Id"));
        equal("2024-11-05", session.operation("tools/call", "{\"name\":\"x\"}", 3).headers().get("MCP-Protocol-Version"));
    }

    private static void httpCounterOfferTo2024() {
        java.util.List<String> offered = new java.util.ArrayList<>();
        var session = new McpFlow(2048, 4).openHttp(request -> {
            if (request.body().contains("server/discover")) return new McpFlow.HttpResponse(404, Map.of(), "", true);
            if (request.body().contains("notifications/initialized")) return new McpFlow.HttpResponse(202, Map.of(), "", true);
            String version = request.headers().get("MCP-Protocol-Version");
            offered.add(version);
            return version.equals(McpRequests.SSE_VERSION)
                ? new McpFlow.HttpResponse(200, Map.of("Mcp-Session-Id", "old"),
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}", true)
                : new McpFlow.HttpResponse(400, Map.of(),
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"data\":{\"supported\":[\"2024-11-05\"],\"requested\":\"" + version + "\"}}}", true);
        }, "client", "1");
        equal(java.util.List.of(McpRequests.HANDSHAKE_VERSION, "2024-11-05"), offered);
        equal("2024-11-05", session.version());
        equal("old", session.sessionId());
    }

    private static void sseTransportNegotiatesNewerVersion() {
        java.util.ArrayDeque<String> events = new java.util.ArrayDeque<>();
        events.add("event: endpoint\ndata: /message\n\n");
        events.add("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-03-26\"}}\n\n");
        events.add("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":4,\"result\":{\"tools\":[]}}\n\n");
        java.util.List<McpRequests.Request> posts = new java.util.ArrayList<>();
        McpFlow.SsePort port = new McpFlow.SsePort() {
            public String nextEvent() { return events.poll(); }
            public McpFlow.HttpResponse post(String endpoint, McpRequests.Request request) {
                posts.add(request);
                return new McpFlow.HttpResponse(202, Map.of(), "", true);
            }
        };
        var flow = new McpFlow(2048, 4);
        var sse = flow.openSse(port, "https://example.test/sse", "client", "1");
        equal("2025-03-26", sse.version());
        equal("/message", sse.endpoint());
        equal(McpRequests.SSE_VERSION, posts.get(0).headers().get("MCP-Protocol-Version"));
        equal("2025-03-26", posts.get(1).headers().get("MCP-Protocol-Version"));
        var list = sse.request("tools/list", null, 4);
        equal(null, list.headers().get("MCP-Session-Id"));
        equal(4L, flow.callSse(port, sse, list, 4).id());
    }

    private static void httpNotificationOn2024KeepsSessionId() {
        var session = new McpFlow.Session(McpRequests.SSE_VERSION, "sid", null, "client", "1");
        equal("sid", session.notification("notifications/cancelled", "{\"requestId\":1}").headers().get("MCP-Session-Id"));
        equal("sid", session.request("tools/list", null).headers().get("MCP-Session-Id"));
        equal("sid", McpRequests.catalog("tools/list", McpRequests.SSE_VERSION, "sid", null).headers().get("MCP-Session-Id"));
    }

    private static final class EmptySsePort implements McpFlow.SsePort {
        public String nextEvent() { return null; }
        public McpFlow.HttpResponse post(String endpoint, McpRequests.Request request) { return null; }
    }

    private static void clientProfile() {
        var info = new ClientInfo("n", "1", "T", "D", "https://e.test", java.util.List.of(
            new ClientIcon("https://e.test/i.png", "image/png", java.util.List.of("48x48"), "light")));
        var profile = new ClientProfile(info, "{\"roots\":{}}");
        String expected = "{\"name\":\"n\",\"version\":\"1\",\"title\":\"T\",\"description\":\"D\",\"websiteUrl\":\"https://e.test\","
            + "\"icons\":[{\"src\":\"https://e.test/i.png\",\"mimeType\":\"image/png\",\"sizes\":[\"48x48\"],\"theme\":\"light\"}]}";
        equal(expected, info.json());
        equal("{\"name\":\"n\",\"version\":\"1\"}", ClientInfo.of("n", "1").json());
        equal("{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{\"roots\":{}},\"clientInfo\":" + expected + "}",
            McpRequests.initializeParams("2025-06-18", profile));
        equal("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":" + McpRequests.initializeParams("2024-11-05", profile) + "}",
            McpRequests.initialize("2024-11-05", profile).body());
        String modern = McpRequests.modern("tools/list", profile, null).body();
        if (!modern.contains("\"io.modelcontextprotocol/clientInfo\":" + expected)
            || !modern.contains("\"io.modelcontextprotocol/clientCapabilities\":{\"roots\":{}}")) throw new AssertionError(modern);
        if (!McpRequests.modernOperation("tools/call", profile, "{\"name\":\"x\"}").body().contains(expected))
            throw new AssertionError("operation clientInfo missing");
        if (!McpRequests.notification("notifications/x", "{}", McpRequests.MODERN_VERSION, null, profile).body().contains(expected))
            throw new AssertionError("notification clientInfo missing");
        equal("{\"name\":\"a\\\"b\",\"version\":\"1\"}", ClientInfo.of("a\"b", "1").json());
        expectFailure("capabilities object required", () -> new ClientProfile(info, "[]"));
        expectFailure("icon src required", () -> new ClientIcon(" ", null, null, null));
        equal("n", new McpFlow.Session("2025-06-18", null, null, profile).clientName());
    }

    private static void equal(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + " got " + actual);
    }

    private static void expectFailure(String expectedMessagePart, Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) {
            if (expected.getMessage() == null || !expected.getMessage().contains(expectedMessagePart))
                throw new AssertionError("Expected message containing '" + expectedMessagePart + "' got '" + expected.getMessage() + "'");
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException containing '" + expectedMessagePart + "'");
    }
}
