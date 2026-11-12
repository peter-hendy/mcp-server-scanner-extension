package com.mcpscanner.cvetargets.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mcpscanner.client.TransportType;

public enum ManifestTransport {
    @JsonProperty("streamable-http") STREAMABLE_HTTP(TransportType.AUTO, ReadinessProbeType.MCP_INITIALIZE),
    @JsonProperty("sse") SSE(TransportType.SSE, ReadinessProbeType.SSE_ENDPOINT);

    private final TransportType clientTransport;
    private final ReadinessProbeType readinessProbe;

    ManifestTransport(TransportType clientTransport, ReadinessProbeType readinessProbe) {
        this.clientTransport = clientTransport;
        this.readinessProbe = readinessProbe;
    }

    public TransportType clientTransport() {
        return clientTransport;
    }

    public ReadinessProbeType readinessProbe() {
        return readinessProbe;
    }
}
