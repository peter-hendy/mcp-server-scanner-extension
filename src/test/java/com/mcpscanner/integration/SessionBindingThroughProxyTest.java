package com.mcpscanner.integration;

import burp.api.montoya.http.Http;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.checks.catalog.sessionbinding.SessionBindingCheck;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.integration.IdentityToolCalls.base;
import static org.assertj.core.api.Assertions.assertThat;

class SessionBindingThroughProxyTest {

    private static final Principal ALICE = new Principal(Principal.PRIMARY_ID, "alice", PrincipalRole.PRIMARY,
            () -> Map.of("Authorization", "Bearer alice-token"), Optional.of(IdentityFixtureServer.MARKER), Map.of());
    private static final Principal BOB = new Principal("bob", "bob", PrincipalRole.PEER,
            () -> Map.of("Authorization", "Bearer bob-token"), Optional.empty(), Map.of());

    private final McpEventLog eventLog = new McpEventLog(null);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void bindingOnTheLiveSessionFiresAndTheLiveSessionSurvivesTheWholeRun() throws Exception {
        try (IdentityFixtureServer server = IdentityFixtureServer.statefulSessions(false);
             ScannerProxyFixture proxy = ScannerProxyFixture.connect(server.endpoint(), TransportType.STREAMABLE_HTTP)) {
            String live = proxy.session().scannerHeaders().get(McpHeaders.SESSION_ID);
            Http http = proxy.routed(new VerbatimHeaderHttp());
            base(http, proxy, "seed_private_record");

            List<AuditIssue> issues = check().audit(base(http, proxy, "read_session_record"), http);

            assertThat(proxy.session().scannerHeaders()).containsEntry(McpHeaders.SESSION_ID, live);
            assertThat(server.deleted).doesNotContain(live);
            assertThat(decisions()).doesNotContain("baseline_unstable");
            assertThat(issues).anySatisfy(issue -> assertThat(issue.detail()).contains("Probe: session."));
            assertThat(server.operations).contains("bob:read_session_record@"
                    + proxy.session().negotiatedProtocolVersion() + "#" + live);
        }
    }

    @Test
    void boundSessionsAreSilentAndTheLiveSessionSurvives() throws Exception {
        try (IdentityFixtureServer server = IdentityFixtureServer.statefulSessions(true);
             ScannerProxyFixture proxy = ScannerProxyFixture.connect(server.endpoint(), TransportType.STREAMABLE_HTTP)) {
            String live = proxy.session().scannerHeaders().get(McpHeaders.SESSION_ID);
            Http http = proxy.routed(new VerbatimHeaderHttp());
            base(http, proxy, "seed_private_record");

            List<AuditIssue> issues = check().audit(base(http, proxy, "read_session_record"), http);

            assertThat(issues).isEmpty();
            assertThat(proxy.session().scannerHeaders()).containsEntry(McpHeaders.SESSION_ID, live);
            assertThat(server.deleted).doesNotContain(live);
            assertThat(decisions()).contains("session_binding:peer_denied").doesNotContain("baseline_unstable");
        }
    }

    private SessionBindingCheck check() {
        return new SessionBindingCheck(eventLog, () -> new BearerTokenAuthStrategy("alice-token"),
                () -> PrincipalRoster.of(ALICE, List.of(BOB)), item -> true);
    }

    private List<String> decisions() {
        return eventLog.snapshot().stream().map(McpEventLog.LogEntry::message)
                .map(message -> message.substring(message.lastIndexOf(": ") + 2)).toList();
    }
}
