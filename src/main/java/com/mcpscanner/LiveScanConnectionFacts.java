package com.mcpscanner;

import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.scan.ScanConnectionFacts;

import java.util.Objects;

final class LiveScanConnectionFacts implements ScanConnectionFacts {

    private final McpScannerSession session;
    private final IdentityRegistry identities;

    LiveScanConnectionFacts(McpScannerSession session, IdentityRegistry identities) {
        this.session = session;
        this.identities = identities;
    }

    @Override
    public String transport() {
        return Objects.toString(session.transportType(), "");
    }

    @Override
    public String connectAs() {
        return identities.primaryUser().name();
    }
}
