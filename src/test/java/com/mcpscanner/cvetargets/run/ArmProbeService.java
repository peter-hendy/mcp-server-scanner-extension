package com.mcpscanner.cvetargets.run;

import burp.api.montoya.http.HttpService;
import com.mcpscanner.mcp.SseProbeStreams;

import java.util.Optional;
import java.util.function.Supplier;

public final class ArmProbeService implements Supplier<Optional<HttpService>> {

    private static final Binding NONE = new Binding(null, Optional::empty, Optional::empty);

    private volatile Binding bound = NONE;

    @Override
    public Optional<HttpService> get() {
        return bound.service().get();
    }

    public Supplier<Optional<SseProbeStreams>> sseProbeStreams() {
        return () -> bound.streams().get();
    }

    synchronized void bind(ArmTeardown owner, Supplier<Optional<HttpService>> armProbeService) {
        bind(owner, armProbeService, Optional::empty);
    }

    synchronized void bind(ArmTeardown owner, Supplier<Optional<HttpService>> armProbeService,
                           Supplier<Optional<SseProbeStreams>> armSseProbeStreams) {
        if (!owner.isClosed()) {
            bound = new Binding(owner, armProbeService, armSseProbeStreams);
        }
    }

    synchronized void clear(ArmTeardown owner) {
        if (bound.owner() == owner) {
            bound = NONE;
        }
    }

    private record Binding(ArmTeardown owner, Supplier<Optional<HttpService>> service,
                           Supplier<Optional<SseProbeStreams>> streams) {
    }
}
