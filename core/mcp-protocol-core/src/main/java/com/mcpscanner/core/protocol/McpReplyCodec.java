package com.mcpscanner.core.protocol;

public final class McpReplyCodec {
    private final int maxBytes;
    private final int maxEvents;

    public McpReplyCodec(int maxBytes, int maxEvents) {
        if (maxBytes <= 0 || maxEvents <= 0) throw new IllegalArgumentException("positive bounds required");
        this.maxBytes = maxBytes;
        this.maxEvents = maxEvents;
    }

    public McpResponses.Reply parse(McpFlow.HttpResponse response, long expectedId) {
        return parse(response, McpRequestId.of(expectedId)).numeric();
    }

    public McpResponses.GenericReply parse(McpFlow.HttpResponse response, McpRequestId expectedId) {
        return parse(response, expectedId, ServerRequestHandler.disabled(), request -> {});
    }

    public McpResponses.GenericReply parse(McpFlow.HttpResponse response, McpRequestId expectedId,
                                           ServerRequestHandler handler,
                                           java.util.function.Consumer<McpRequests.Request> sender) {
        if (response == null || !response.complete()) throw new IllegalArgumentException("complete response required");
        if (!eventStream(response)) return jsonReply(response.body(), expectedId);
        return read(bufferedEvents(response.body()), expectedId, handler, sender);
    }

    public McpResponses.GenericReply read(McpEventSource events, McpRequestId expectedId,
                                          ServerRequestHandler handler,
                                          java.util.function.Consumer<McpRequests.Request> sender) {
        for (int count = 0; count < maxEvents; count++) {
            String event = events.nextEvent();
            if (event == null) throw new IllegalArgumentException("SSE closed before reply");
            var reply = dispatch(event, expectedId, handler, sender);
            if (reply != null) return reply;
        }
        throw new IllegalArgumentException("SSE reply event limit");
    }

    private McpResponses.GenericReply dispatch(String event, McpRequestId expectedId, ServerRequestHandler handler,
                                               java.util.function.Consumer<McpRequests.Request> sender) {
        var request = McpSse.serverRequest(event, maxBytes);
        if (request == null) return McpSse.reply(event, expectedId, maxBytes);
        handler.reply(request).ifPresent(sender);
        return null;
    }

    private McpResponses.GenericReply jsonReply(String body, McpRequestId expectedId) {
        var reply = McpResponses.parseGeneric(body, maxBytes);
        if (!reply.id().equals(expectedId)) throw new IllegalArgumentException("reply ID mismatch");
        return reply;
    }

    private McpEventSource bufferedEvents(String body) {
        if (body == null || body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes)
            throw new IllegalArgumentException("SSE response exceeds limit");
        String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.endsWith("\n\n")) throw new IllegalArgumentException("incomplete SSE response");
        String[] events = normalized.split("(?<=\n\n)");
        if (events.length > maxEvents) throw new IllegalArgumentException("SSE event limit");
        var remaining = new java.util.ArrayDeque<String>();
        for (String event : events) if (!event.isBlank()) remaining.add(event);
        return remaining::poll;
    }

    public String json(McpResponses.Reply reply) {
        if (reply == null) throw new IllegalArgumentException("reply required");
        return jsonGeneric(new McpResponses.GenericReply(McpRequestId.of(reply.id()), reply.result(), reply.error()));
    }

    public String jsonGeneric(McpResponses.GenericReply reply) {
        if (reply == null || (reply.result() == null) == (reply.error() == null))
            throw new IllegalArgumentException("exactly one response branch required");
        java.util.Map<String, Object> document = new java.util.LinkedHashMap<>();
        document.put("jsonrpc", "2.0");
        document.put("id", reply.id().value());
        document.put(reply.result() != null ? "result" : "error",
            reply.result() != null ? reply.result() : reply.error());
        return JsonWriter.write(document, maxBytes);
    }

    static boolean eventStream(McpFlow.HttpResponse response) {
        if (response.headers() == null) return false;
        for (java.util.Map.Entry<String, String> header : response.headers().entrySet())
            if (header.getKey().equalsIgnoreCase("Content-Type") && header.getValue() != null
                && header.getValue().toLowerCase(java.util.Locale.ROOT).startsWith("text/event-stream")) return true;
        return false;
    }
}
