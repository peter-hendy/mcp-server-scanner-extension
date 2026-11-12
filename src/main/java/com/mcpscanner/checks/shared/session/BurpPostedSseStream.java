package com.mcpscanner.checks.shared.session;

import com.mcpscanner.mcp.SseProbeStream;
import com.mcpscanner.core.connection.SseStreamFactory;
import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpRequests;

final class BurpPostedSseStream implements SseStreamFactory.Stream {

    private final SseProbeStream stream;
    private final BurpSessionPorts ports;
    private boolean endpointAnnounced;

    BurpPostedSseStream(SseProbeStream stream, BurpSessionPorts ports) {
        this.stream = stream;
        this.ports = ports;
    }

    @Override
    public String nextEvent() {
        if (!endpointAnnounced) {
            endpointAnnounced = true;
            return "event: endpoint\ndata: " + stream.messageUrl() + "\n\n";
        }
        return stream.nextEvent();
    }

    @Override
    public McpFlow.HttpResponse post(String endpoint, McpRequests.Request request) {
        McpFlow.HttpResponse response = ports.post(request);
        stream.beginReplyWait();
        return response;
    }

    @Override
    public void close() {
        stream.close();
    }
}
