package com.mcpscanner.cvetargets.shared;

import com.mcpscanner.cvetargets.manifest.AuthSetup;
import com.mcpscanner.cvetargets.manifest.Download;
import com.mcpscanner.cvetargets.manifest.EndpointSpec;
import com.mcpscanner.cvetargets.manifest.IdentitySetup;
import com.mcpscanner.cvetargets.manifest.ManifestTransport;
import com.mcpscanner.cvetargets.manifest.ReadinessProbeType;

import java.util.List;

record BuildFingerprint(String buildFiles, String compose, String service, EndpointSpec endpoint,
                        ManifestTransport transport, AuthSetup auth, ReadinessProbeType readiness,
                        IdentitySetup identities, List<Download> downloads) {

    static BuildFingerprint of(ManifestArm member) {
        var manifest = member.target().manifest();
        var arm = member.arm();
        return new BuildFingerprint(BuildFiles.identity(member.target()), manifest.compose(),
                arm.service(), arm.endpoint(), arm.transport(), manifest.auth().including(arm.headers()),
                manifest.readiness().probe(), manifest.identities(), manifest.downloads());
    }
}
