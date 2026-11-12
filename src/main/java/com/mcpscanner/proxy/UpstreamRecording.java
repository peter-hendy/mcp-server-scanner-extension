package com.mcpscanner.proxy;

import com.mcpscanner.mcp.LegacySseRoutes;

import java.util.Optional;

public record UpstreamRecording(UpstreamCaptures captures, UpstreamExchangeStore store,
                                LegacySseRoutes legacySseRoutes) {

    public static UpstreamRecording create() {
        return new UpstreamRecording(new UpstreamCaptures(), new UpstreamExchangeStore(), new LegacySseRoutes());
    }

    ForwardCapture open() {
        return captures.open();
    }

    Optional<String> keep(ForwardCapture capture, String forwardedBody) {
        return capture.chosenFor(forwardedBody).map(store::put);
    }
}
