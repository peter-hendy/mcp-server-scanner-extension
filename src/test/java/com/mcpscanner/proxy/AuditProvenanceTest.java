package com.mcpscanner.proxy;

import com.mcpscanner.mcp.ScannerSentinels;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuditProvenanceTest {
    @Test
    void absentConfiguredCredentialIsBoundWhileOrdinaryWireHeadersCanChange() {
        var provenance = new AuditProvenance();
        String marker = provenance.issue(Map.of("Host", "upstream", "Content-Length", "100"),
                Set.of("User-Agent"));
        assertThat(provenance.classify(Map.of(ScannerSentinels.AUDIT_CAPABILITY_HEADER, marker,
                "Host", "127.0.0.1", "Content-Length", "200")))
                .isEqualTo(AuditProvenance.Status.MANAGED);
        assertThat(provenance.classify(Map.of(ScannerSentinels.AUDIT_CAPABILITY_HEADER, marker,
                "user-agent", "identity-B")))
                .isEqualTo(AuditProvenance.Status.MANUAL);
    }

    @Test
    void operationHeadersTheProxyDerivesFromTheBodyNeverMakeAManagedRequestManual() {
        var provenance = new AuditProvenance();
        String marker = provenance.issue(Map.of(), Set.of("Authorization"));

        assertThat(provenance.classify(Map.of(ScannerSentinels.AUDIT_CAPABILITY_HEADER, marker,
                "Mcp-Method", "tools/call", "Mcp-Name", "lookup", "MCP-Protocol-Version", "2026-07-28",
                "Mcp-Param-Tenant", "fuzzed", "Mcp-Param-Priority", "1")))
                .isEqualTo(AuditProvenance.Status.MANAGED);
    }

    @Test
    void anUnboundIdentityHeaderBesideParamHeadersStillMakesTheRequestManual() {
        var provenance = new AuditProvenance();
        String marker = provenance.issue(Map.of(), Set.of("Authorization"));

        assertThat(provenance.classify(Map.of(ScannerSentinels.AUDIT_CAPABILITY_HEADER, marker,
                "Mcp-Param-Tenant", "fuzzed", "X-Api-Key", "identity-B")))
                .isEqualTo(AuditProvenance.Status.MANUAL);
    }
}
