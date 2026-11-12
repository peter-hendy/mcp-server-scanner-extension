package com.mcpscanner.core.connection;

import com.mcpscanner.core.protocol.ClientProfile;
import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpRequests;
import com.mcpscanner.core.protocol.McpRequestId;
import com.mcpscanner.core.protocol.McpResponses;
import java.util.Map;

public final class SseSession implements AutoCloseable {
    private final McpFlow flow;
    private final McpConnection connection;
    private final SseStreamFactory factory;
    private final String origin;
    private final ClientProfile client;
    private final SessionExpiry expiry;
    private McpFlow.Session session;
    private SseStreamFactory.Stream stream;
    private boolean established;
    private boolean closed;

    SseSession(McpFlow flow, McpConnection connection, SseStreamFactory factory, String origin, ClientProfile client,
               SessionExpiry expiry) {
        this.flow = flow;
        this.connection = connection;
        this.factory = factory;
        this.origin = origin;
        this.client = client;
        this.expiry = expiry;
        execute(() -> null);
        established = true;
    }

    public McpFlow.Session session() {
        if (closed || session == null) throw new IllegalStateException("SSE session is closed");
        return session;
    }

    public McpResponses.Reply call(McpRequests.Request request, long expectedId) {
        java.util.Objects.requireNonNull(request);
        return execute(() -> flow.callSse(stream, session, request, McpRequestId.of(expectedId))).numeric();
    }

    public McpResponses.GenericReply call(McpRequests.Request request, McpRequestId expectedId) {
        java.util.Objects.requireNonNull(request);
        java.util.Objects.requireNonNull(expectedId);
        return execute(() -> flow.callSse(stream, session, request, expectedId));
    }

    public McpFlow.HttpResponse sendNotification(McpRequests.Request notification) {
        java.util.Objects.requireNonNull(notification);
        var document = (Map<?, ?>) com.mcpscanner.core.protocol.JsonDocuments.parse(notification.body(), 65536);
        if (document.containsKey("id")) throw new IllegalArgumentException("Notification cannot contain an ID");
        return execute(() -> stream.post(session.endpoint(), notification));
    }

    private <T> T execute(java.util.function.Supplier<T> operation) {
        if (closed) throw new IllegalStateException("SSE session is closed");
        var recovery = connection.beginRecovery();
        for (int attempt = 0; ; attempt++) {
            try {
                if (stream == null) reconnect();
                return operation.get();
            } catch (CredentialsChanged failure) {
                closeCurrent();
                if (attempt == 2) throw new ConnectionAuthException(RecoveryResult.STOP);
            } catch (SseOpenException failure) {
                closeCurrent();
                if (!reconnectable() || !connection.challenged(failure.response()) || attempt == 2
                        || !connection.recover(recovery, failure.response()))
                    throw failure;
            } catch (RuntimeException failure) {
                closeCurrent();
                throw failure;
            }
        }
    }

    private void reconnect() {
        if (!reconnectable()) throw new IllegalStateException("SSE probe session ended");
        connect();
    }

    private boolean reconnectable() {
        return !established || expiry == SessionExpiry.REINITIALIZE;
    }

    private void connect() {
        Map<String, String> credentials = connection.credentialHeaders();
        var opened = factory.open(credentials);
        stream = new SseStreamFactory.Stream() {
            public String nextEvent() { return opened.nextEvent(); }
            public McpFlow.HttpResponse post(String endpoint, McpRequests.Request request) {
                if (!credentials.equals(connection.credentialHeaders())) throw new CredentialsChanged();
                var response = opened.post(endpoint, connection.attach(request, credentials));
                if (connection.challenged(response)) throw new SseOpenException(response);
                return response;
            }
            public void close() { opened.close(); }
        };
        session = flow.openSse(stream, origin, client);
    }

    private void closeCurrent() {
        var previous = stream;
        stream = null;
        session = null;
        if (previous != null) previous.close();
    }

    @Override public void close() {
        closed = true;
        closeCurrent();
    }

    private static final class CredentialsChanged extends RuntimeException {}
}
