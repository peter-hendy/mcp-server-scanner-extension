package com.mcpscanner.client;

import com.mcpscanner.mcp.BoundedBodyReader;
import com.mcpscanner.mcp.InFlightServerPings;
import com.mcpscanner.mcp.ServerPings;
import com.mcpscanner.mcp.SseFrame;
import com.mcpscanner.mcp.SseResponseParser;
import com.mcpscanner.core.connection.SseStreamFactory;
import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpRequests;
import com.mcpscanner.core.protocol.McpSse;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Function;

final class DiscoveredSseStream implements SseStreamFactory.Stream {

    private static final long ADMISSION_REQUEST_ID = 1L;

    private final URI messageUrl;
    private final SseReplyReader reader;
    private final Consumer<String> admissionReplies;
    private final InFlightServerPings inFlightPings;
    private final Function<McpRequests.Request, McpFlow.HttpResponse> poster;
    private boolean endpointAnnounced;

    DiscoveredSseStream(URI messageUrl, SseReplyReader reader, Consumer<String> admissionReplies,
                        ServerPings serverPings, Function<McpRequests.Request, McpFlow.HttpResponse> poster) {
        this.messageUrl = messageUrl;
        this.reader = reader;
        this.admissionReplies = admissionReplies;
        this.poster = poster;
        this.inFlightPings = new InFlightServerPings(serverPings, this::postServerRequestReply);
    }

    @Override
    public String nextEvent() {
        if (!endpointAnnounced) {
            endpointAnnounced = true;
            return endpointEvent();
        }
        String frame = reader.nextEvent();
        while (frame != null && answeredServerRequest(frame)) {
            frame = reader.nextEvent();
        }
        if (frame != null && McpSse.reply(frame, ADMISSION_REQUEST_ID, BoundedBodyReader.MAX_RESPONSE_BYTES) != null) {
            admissionReplies.accept(SseResponseParser.extractJsonRpcResponse(frame));
        }
        return frame;
    }

    @Override
    public McpFlow.HttpResponse post(String endpoint, McpRequests.Request request) {
        inFlightPings.callStarted(request);
        McpFlow.HttpResponse response = poster.apply(request);
        reader.beginReplyWait();
        return response;
    }

    @Override
    public void close() {
        reader.close();
    }

    private boolean answeredServerRequest(String frame) {
        SseFrame event = SseFrame.parse(frame);
        return event.isMessage() && event.hasData() && inFlightPings.consume(event.data());
    }

    private void postServerRequestReply(McpRequests.Request reply) {
        try {
            poster.apply(reply);
        } catch (IllegalStateException replyNotDelivered) {
        }
    }

    private String endpointEvent() {
        String query = messageUrl.getRawQuery() == null ? "" : "?" + messageUrl.getRawQuery();
        return "event: endpoint\ndata: " + messageUrl.getRawPath() + query + "\n\n";
    }
}
