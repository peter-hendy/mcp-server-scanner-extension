package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.api.HostSettlement;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.checks.shared.stream.ScriptedEventStreams;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.LOOPBACK_URL;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.TOOLS_RESULT;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.ok;
import static com.mcpscanner.checks.catalog.originhostadmission.AdmissionServers.toolsList;
import static com.mcpscanner.checks.catalog.originhostadmission.SessionDroppingServer.DROPPED_SESSION;
import static org.assertj.core.api.Assertions.assertThat;

class DroppedSessionAdmissionTest {

    private static final String ORIGIN_ISSUE = "MCP Origin header admission";
    private static final String HOST_ISSUE = "MCP Host header admission";
    private static final String PAIR_ISSUE = "MCP Host and Origin header admission";

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void probesInAFreshSessionWhenTheCapturedSessionWasDropped() {
        SessionDroppingServer server = SessionDroppingServer.admittingEveryHeader();

        assertThat(check().audit(capturedInDroppedSession(), server.http)).extracting(AuditIssue::name)
                .containsExactly(ORIGIN_ISSUE, HOST_ISSUE, PAIR_ISSUE);
        assertThat(server.sessionsUsedFor("tools/list")).hasSize(8)
                .startsWith(DROPPED_SESSION).containsOnly(DROPPED_SESSION, "fresh-1");
    }

    @Test
    void aFreshSessionStillJudgesAServerThatChecksHost() {
        SessionDroppingServer server = SessionDroppingServer.admitting(request ->
                "127.0.0.1:8765".equals(SessionDroppingServer.header(request, "Host")));

        assertThat(check().audit(capturedInDroppedSession(), server.http)).extracting(AuditIssue::name)
                .containsExactly(ORIGIN_ISSUE);
    }

    @Test
    void deletesTheFreshSessionAndNeverTheCapturedOne() {
        SessionDroppingServer server = SessionDroppingServer.admittingEveryHeader();

        check().audit(capturedInDroppedSession(), server.http);

        assertThat(server.sessionsIssued()).isEqualTo(1);
        assertThat(server.deleted).containsExactly("fresh-1");
        assertThat(server.sessionsUsedFor(null)).doesNotContain(DROPPED_SESSION);
    }

    @Test
    void aServerThatIssuesNoSessionStaysUnprobed() {
        SessionDroppingServer server = SessionDroppingServer.issuingNoSessions();

        HostSettlement settlement = check().trials().get(0).run(capturedInDroppedSession(), server.http).settlement();

        assertThat(settlement).isEqualTo(HostSettlement.NOT_PROBED);
        assertThat(server.sessionsUsedFor("tools/list")).containsExactly(DROPPED_SESSION);
    }

    @Test
    void aBaselineWithoutASessionIsNotRetried() {
        SessionDroppingServer server = SessionDroppingServer.admittingEveryHeader();

        HostSettlement settlement = check().trials().get(0)
                .run(HttpRequestResponse.httpRequestResponse(toolsList(LOOPBACK_URL), ok(TOOLS_RESULT)), server.http)
                .settlement();

        assertThat(settlement).isEqualTo(HostSettlement.NOT_PROBED);
        assertThat(server.sessionsIssued()).isZero();
    }

    @Test
    void aFreshSessionThatAlsoRefusesStaysUnprobedAndIsDeleted() {
        SessionDroppingServer server = SessionDroppingServer.refusingFreshSessionsToo();

        HostSettlement settlement = check().trials().get(0).run(capturedInDroppedSession(), server.http).settlement();

        assertThat(settlement).isEqualTo(HostSettlement.NOT_PROBED);
        assertThat(server.deleted).containsExactly("fresh-1");
    }

    @Test
    void aStreamedReplyInTheFreshSessionStillDeletesTheFreshSession() {
        SessionDroppingServer server = SessionDroppingServer.streamingInFreshSessions();

        check().trials().get(0).run(capturedInDroppedSession(), server.http);

        assertThat(server.deleted).containsExactly("fresh-1");
    }

    @Test
    void theOwnSseStreamRouteNeverOpensAFreshSession() {
        SessionDroppingServer server = SessionDroppingServer.admittingEveryHeader();
        OriginHostAdmissionCheck check = new OriginHostAdmissionCheck(McpEventLog.noop(),
                new CredentialHeaders(NoAuthStrategy::new),
                () -> Optional.of(HttpService.httpService("127.0.0.1", 40000, false)),
                ScriptedEventStreams.none(), new ProbeSessions(NoAuthStrategy::new));

        check.trials().get(0).run(capturedInDroppedSession(), server.http);

        assertThat(server.sessionsIssued()).isZero();
    }

    private static HttpRequestResponse capturedInDroppedSession() {
        HttpRequest seed = toolsList(LOOPBACK_URL).withAddedHeader("Mcp-Session-Id", DROPPED_SESSION);
        return HttpRequestResponse.httpRequestResponse(seed, ok(TOOLS_RESULT));
    }

    private static OriginHostAdmissionCheck check() {
        return new OriginHostAdmissionCheck(McpEventLog.noop(), new CredentialHeaders(NoAuthStrategy::new),
                Optional::empty, ScriptedEventStreams.none(), new ProbeSessions(NoAuthStrategy::new));
    }
}
