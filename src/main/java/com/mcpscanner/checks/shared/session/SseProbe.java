package com.mcpscanner.checks.shared.session;

import com.mcpscanner.core.connection.SseProbeSession;

import java.util.Optional;

public final class SseProbe implements AutoCloseable {

    private final SseProbeSession session;
    private final String messageUrl;
    private final String liveMessageUrl;

    SseProbe(SseProbeSession session, String messageUrl, String liveMessageUrl) {
        this.session = session;
        this.messageUrl = messageUrl;
        this.liveMessageUrl = liveMessageUrl;
    }

    public Optional<String> sessionId() {
        return SessionHeaders.legacySseSessionOf(messageUrl, liveMessageUrl);
    }

    @Override
    public void close() {
        session.close();
    }
}
