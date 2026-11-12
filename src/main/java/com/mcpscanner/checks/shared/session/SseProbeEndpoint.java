package com.mcpscanner.checks.shared.session;

import com.mcpscanner.mcp.SseProbeStream;
import com.mcpscanner.mcp.SseProbeStreams;
import com.mcpscanner.core.connection.McpConnection;
import com.mcpscanner.core.connection.SseProbeSession;
import com.mcpscanner.core.connection.SseStreamFactory;
import com.mcpscanner.core.protocol.ClientProfile;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class SseProbeEndpoint {

    private final McpConnection connection;
    private final SseProbeStreams streams;
    private final Function<String, BurpSessionPorts> ports;
    private final String liveMessageUrl;
    private final ClientProfile client;

    SseProbeEndpoint(McpConnection connection, SseProbeStreams streams, Function<String, BurpSessionPorts> ports,
                     String liveMessageUrl, ClientProfile client) {
        this.connection = connection;
        this.streams = streams;
        this.ports = ports;
        this.liveMessageUrl = liveMessageUrl;
        this.client = client;
    }

    public SseProbe open() {
        AtomicReference<String> messageUrl = new AtomicReference<>();
        SseProbeSession session = connection.openSseProbe(headers -> stream(headers, messageUrl), liveMessageUrl, client);
        return new SseProbe(session, messageUrl.get(), liveMessageUrl);
    }

    private SseStreamFactory.Stream stream(Map<String, String> headers, AtomicReference<String> messageUrl) {
        SseProbeStream opened = streams.open(headers);
        messageUrl.set(opened.messageUrl());
        return new BurpPostedSseStream(opened, ports.apply(opened.messageUrl()));
    }
}
