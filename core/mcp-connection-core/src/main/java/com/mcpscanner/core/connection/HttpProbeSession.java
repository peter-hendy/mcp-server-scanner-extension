package com.mcpscanner.core.connection;

import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpRequests;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public final class HttpProbeSession implements AutoCloseable {
    private final McpConnection connection;
    private final HttpSession session;
    private final SessionDeletePort deletePort;
    private boolean terminated;

    HttpProbeSession(McpConnection connection, HttpSession session, SessionDeletePort deletePort) {
        this.connection = requireNonNull(connection);
        this.session = requireNonNull(session);
        this.deletePort = requireNonNull(deletePort);
    }

    public McpFlow.Session session() { return session.session(); }

    public Optional<String> sessionId() { return Optional.ofNullable(session().sessionId()); }

    public McpFlow.HttpResponse send(McpRequests.Request request) { return session.send(request); }

    public synchronized McpFlow.HttpResponse terminate() {
        String sessionId = sessionId().orElseThrow(() -> new IllegalStateException("The server issued no session ID"));
        terminated = true;
        return connection.terminate(deletePort, sessionId, session().version());
    }

    @Override public synchronized void close() {
        if (!terminated && sessionId().isPresent()) terminate();
    }
}
