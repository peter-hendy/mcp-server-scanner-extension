package com.mcpscanner.cvetargets.manifest;

import java.util.Map;

public record TargetArm(
        String name,
        ArmRole role,
        String version,
        String pin,
        String service,
        EndpointSpec endpoint,
        ManifestTransport transport,
        ExpectedOutcome expected,
        Map<String, String> headers) {

    public TargetArm {
        transport = transport == null ? ManifestTransport.STREAMABLE_HTTP : transport;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public TargetArm(String name, ArmRole role, String version, String pin, String service, EndpointSpec endpoint,
                     ManifestTransport transport, ExpectedOutcome expected) {
        this(name, role, version, pin, service, endpoint, transport, expected, Map.of());
    }
}
