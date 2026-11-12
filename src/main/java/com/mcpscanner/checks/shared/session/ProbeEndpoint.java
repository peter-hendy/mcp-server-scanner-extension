package com.mcpscanner.checks.shared.session;

import com.mcpscanner.core.connection.HttpProbeSession;
import com.mcpscanner.core.connection.McpConnection;
import com.mcpscanner.core.connection.SessionDeletePort;
import com.mcpscanner.core.protocol.ClientProfile;
import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpRequests;

public final class ProbeEndpoint {

    private final McpConnection connection;
    private final BurpSessionPorts ports;
    private final SessionDeletePort deletes;
    private final ClientProfile client;
    private final String handshakeVersion;

    ProbeEndpoint(McpConnection connection, BurpSessionPorts ports, SessionDeletePort deletes, ClientProfile client,
                  String handshakeVersion) {
        this.connection = connection;
        this.ports = ports;
        this.deletes = deletes;
        this.client = client;
        this.handshakeVersion = handshakeVersion;
    }

    public HttpProbeSession open() {
        return connection.openInitializeHttpProbe(ports::post, deletes, client, handshakeVersion);
    }

    public HttpProbeSession openWithoutCredentials() {
        return connection.anonymous().openInitializeHttpProbe(ports::post, deletes, client, handshakeVersion);
    }

    public McpFlow.HttpResponse send(McpRequests.Request request) {
        return connection.send(ports::post, request);
    }

    public McpFlow.HttpResponse sendWithoutCredentials(McpRequests.Request request) {
        return connection.anonymous().send(ports::post, request);
    }
}
