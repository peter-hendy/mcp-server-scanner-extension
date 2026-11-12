package com.mcpscanner.proxy;

import com.mcpscanner.client.ProtocolHttpTransport;

import java.util.Optional;

public final class UpstreamCaptures {

    private final ThreadLocal<ForwardCapture> active = new ThreadLocal<>();

    public ProtocolHttpTransport recording(ProtocolHttpTransport delegate) {
        return new RecordingTransport(delegate, this);
    }

    public boolean forwarding() {
        return active.get() != null;
    }

    ForwardCapture open() {
        ForwardCapture capture = new ForwardCapture(active::remove);
        active.set(capture);
        return capture;
    }

    Optional<ForwardCapture> current() {
        return Optional.ofNullable(active.get());
    }

    void record(RecordedExchange exchange) {
        current().ifPresent(capture -> capture.record(() -> exchange));
    }
}
