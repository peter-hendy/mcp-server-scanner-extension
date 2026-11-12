package com.mcpscanner.architecture.connectioncatalog.probeonly;

import com.mcpscanner.core.connection.HttpProbeSession;

import java.util.Optional;

public final class ProbeOnlyCheck {

    public Optional<String> issued(HttpProbeSession probe) {
        return probe.sessionId();
    }
}
