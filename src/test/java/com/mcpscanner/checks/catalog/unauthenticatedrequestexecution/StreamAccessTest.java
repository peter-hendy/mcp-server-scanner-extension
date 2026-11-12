package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.checks.shared.stream.ScriptedEventStreams;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static com.mcpscanner.checks.shared.probe.SimulatedMcpServer.request;
import static com.mcpscanner.checks.shared.probe.SimulatedMcpServer.toolCall;
import static com.mcpscanner.checks.shared.probe.SimulatedMcpServer.toolsList;
import static org.assertj.core.api.Assertions.assertThat;

class StreamAccessTest {

    private static final Map<String, String> AUTHENTICATED =
            Map.of("Content-Type", "application/json", "Authorization", SimulatedMcpServer.VALID_TOKEN);
    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void aSessionStreamThatOpensWithoutCredentialsBehindEnforcedPostsIsLowFirm() {
        ScriptedEventStreams streams = ScriptedEventStreams.inSessions(request -> true);

        AuditIssue issue = streamIssue(audit(bearer(), streams, SimulatedMcpServer.enforcing(), toolCall()));

        assertThat(issue.name()).isEqualTo(FindingKind.SESSION_STREAM.issueName());
        assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.LOW);
        assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
        assertThat(issue.detail()).contains("outside Burp");
        assertThat(issue.requestResponses()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(issue.requestResponses().get(0).request().headerValue("Authorization"))
                .isEqualTo(SimulatedMcpServer.VALID_TOKEN);
        assertThat(issue.requestResponses().get(1).request().hasHeader("Authorization")).isFalse();
        assertThat(issue.requestResponses().get(1).request().headerValue("Mcp-Session-Id"))
                .isEqualTo(ScriptedEventStreams.PROBE_SESSION);
    }

    @Test
    void aStatelessStreamThatOpensWithoutCredentialsBehindEnforcedPostsIsMediumFirm() {
        ScriptedEventStreams streams = ScriptedEventStreams.stateless(request -> true);

        AuditIssue issue = streamIssue(audit(bearer(), streams, SimulatedMcpServer.enforcing(), toolCall()));

        assertThat(issue.name()).isEqualTo(FindingKind.ANONYMOUS_STREAM.issueName());
        assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.MEDIUM);
        assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
    }

    @Test
    void aStreamThatRefusesAnonymousCallersIsSilent() {
        ScriptedEventStreams streams = ScriptedEventStreams.inSessions(credentialed());

        assertThat(audit(bearer(), streams, SimulatedMcpServer.enforcing(), toolCall())).isEmpty();
        assertThat(streams.opened).hasSize(2);
    }

    @Test
    void aServerWithoutAnEventStreamIsSilentAfterTheAuthenticatedControl() {
        ScriptedEventStreams streams = ScriptedEventStreams.inSessions(request -> false);

        assertThat(audit(bearer(), streams, SimulatedMcpServer.enforcing(), toolCall())).isEmpty();
        assertThat(streams.opened).hasSize(1);
    }

    @Test
    void anOpenStreamOnAServerThatEnforcesNothingIsNotAStreamFinding() {
        ScriptedEventStreams streams = ScriptedEventStreams.stateless(request -> true);

        List<AuditIssue> issues = audit(bearer(), streams, SimulatedMcpServer.open(), toolsList());

        assertThat(issues).extracting(AuditIssue::name).doesNotContain(
                FindingKind.ANONYMOUS_STREAM.issueName(), FindingKind.SESSION_STREAM.issueName());
    }

    @Test
    void aScanWithoutCredentialsNeverProbesTheStream() {
        ScriptedEventStreams streams = ScriptedEventStreams.stateless(request -> true);
        SimulatedMcpServer server = SimulatedMcpServer.open();

        check(new NoAuthStrategy(), streams).audit(server.exchange(request(toolCall(), JSON)), server.http());

        assertThat(streams.opened).isEmpty();
    }

    @Test
    void probesEachEndpointsStreamOnce() {
        ScriptedEventStreams streams = ScriptedEventStreams.inSessions(request -> true);
        UnauthenticatedRequestExecutionCheck check = check(bearer(), streams);
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();

        check.audit(server.exchange(request(toolCall(), AUTHENTICATED)), server.http());
        check.audit(server.exchange(request(toolsList(), AUTHENTICATED)), server.http());

        assertThat(streams.opened).hasSize(2);
    }

    @Test
    void anAuthenticatedStreamThatGetsNoAnswerIsRetriedOnALaterRequestAtMostThreeTimes() {
        ScriptedEventStreams streams = ScriptedEventStreams.unanswered();
        UnauthenticatedRequestExecutionCheck check = check(bearer(), streams);
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();

        for (int request = 0; request < 5; request++) {
            check.audit(server.exchange(request(toolCall(), AUTHENTICATED)), server.http());
        }

        assertThat(streams.opened).hasSize(3);
    }

    @Test
    void aServerErrorOnTheAuthenticatedStreamIsRetried() {
        ScriptedEventStreams streams = ScriptedEventStreams.answeringWith(503, false);
        UnauthenticatedRequestExecutionCheck check = check(bearer(), streams);
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();

        check.audit(server.exchange(request(toolCall(), AUTHENTICATED)), server.http());
        check.audit(server.exchange(request(toolCall(), AUTHENTICATED)), server.http());

        assertThat(streams.opened).hasSize(2);
    }

    @Test
    void aStreamTheServerDoesNotOfferIsSettled() {
        ScriptedEventStreams streams = ScriptedEventStreams.answeringWith(405, false);
        UnauthenticatedRequestExecutionCheck check = check(bearer(), streams);
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();

        check.audit(server.exchange(request(toolCall(), AUTHENTICATED)), server.http());
        check.audit(server.exchange(request(toolCall(), AUTHENTICATED)), server.http());

        assertThat(streams.opened).hasSize(1);
    }

    @Test
    void missingEnforcementEvidenceLeavesTheEndpointForALaterRequest() {
        ScriptedEventStreams streams = ScriptedEventStreams.stateless(request -> true);
        UnauthenticatedRequestExecutionCheck check = check(bearer(), streams);
        SimulatedMcpServer server = SimulatedMcpServer.open();

        check.audit(server.exchange(request(toolsList(), AUTHENTICATED)), server.http());
        check.audit(server.exchange(request(toolsList(), AUTHENTICATED)), server.http());

        assertThat(streams.opened).hasSize(4);
    }

    private static Predicate<HttpRequest> credentialed() {
        return request -> request.hasHeader("Authorization");
    }

    private static List<AuditIssue> audit(AuthStrategy strategy, ScriptedEventStreams streams,
                                          SimulatedMcpServer server, String body) {
        HttpRequestResponse base = server.exchange(request(body, AUTHENTICATED));
        return check(strategy, streams).audit(base, server.http());
    }

    private static AuditIssue streamIssue(List<AuditIssue> issues) {
        return issues.stream().filter(issue -> issue.name().contains("event stream")).findFirst().orElseThrow();
    }

    private static UnauthenticatedRequestExecutionCheck check(AuthStrategy strategy, ScriptedEventStreams streams) {
        return new UnauthenticatedRequestExecutionCheck(McpEventLog.noop(), () -> strategy, Optional::empty, streams);
    }

    private static AuthStrategy bearer() {
        return new BearerTokenAuthStrategy("valid-token");
    }
}
