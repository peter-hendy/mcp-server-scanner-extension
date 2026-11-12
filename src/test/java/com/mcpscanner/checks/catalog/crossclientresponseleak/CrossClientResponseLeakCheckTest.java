package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.HostAudits;
import com.mcpscanner.checks.api.HostSettlement;
import com.mcpscanner.checks.api.HostTrialResult;
import com.mcpscanner.checks.api.ImplementedKind;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.ScriptedBurpHttp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CrossClientResponseLeakCheckTest {

    private static final String ISSUE = "MCP response delivered to another client";
    private static final Duration PROBE_TIMEOUT = Duration.ofMillis(300);
    private static final String PREFIX = "Scan check: cross-client-response-leak — ";
    private static final int FULL_ROUND_WITHOUT_CROSSING = 2 + RetriedRound.MAX_ATTEMPTS * LeakTrial.CLIENTS;

    private Optional<HttpService> probeService = Optional.empty();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void declaresAHostCheckWithItsCatalogId() {
        CrossClientResponseLeakCheck check = check();

        assertThat(check.descriptor().id()).isEqualTo("cross-client-response-leak");
        assertThat(check.descriptor().kind()).isEqualTo(CheckKind.HOST);
        assertThat(ImplementedKind.of(check)).isEqualTo(CheckKind.HOST);
        assertThat(check.descriptor().cwes()).isNotEmpty();
    }

    @Test
    void reportsAReplyCarryingAnotherClientsMarker() {
        LeakServers.Server server = LeakServers.misroutingByRequestId();

        List<AuditIssue> issues = check().audit(LeakServers.echoableToolCall(), server.http);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.name()).isEqualTo(ISSUE);
            assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.HIGH);
            assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
            assertThat(issue.requestResponses()).hasSize(2);
        });
    }

    @Test
    void reportsAServerThatMisroutesOnlySomeAttemptsOfEachSameIdRound() {
        LeakServers.Server server = LeakServers.misroutingOnlyEverySecondCollidingBatch();

        List<AuditIssue> issues = check().audit(LeakServers.echoableToolCall(), server.http);

        assertThat(issues).extracting(AuditIssue::name).containsExactly(ISSUE);
    }

    @Test
    void reportsALeakStartingFromAModernToolCall() {
        LeakServers.Server server = LeakServers.misroutingByRequestId();

        List<AuditIssue> issues = check().audit(LeakServers.modernEchoableToolCall(), server.http);

        assertThat(issues).extracting(AuditIssue::name).containsExactly(ISSUE);
    }

    @Test
    void racesAnEchoingServerStartingFromAModernToolCall() {
        LeakServers.Server server = LeakServers.echoing();

        assertThat(check().audit(LeakServers.modernEchoableToolCall(), server.http)).isEmpty();
        assertThat(server.requestsSeen()).isEqualTo(FULL_ROUND_WITHOUT_CROSSING);
    }

    @Test
    void staysSilentWhenConcurrentCallsOverwriteOneSharedRecord() {
        LeakServers.Server server = LeakServers.overwritingOneSharedRecordForCollidingIds();

        assertThat(check().audit(LeakServers.echoableToolCall(), server.http)).isEmpty();
    }

    @Test
    void staysSilentWhenRepliesCrossWhateverTheRequestIds() {
        LeakServers.Server server = LeakServers.rotatingRepliesAmongConcurrentCallers();

        assertThat(check().audit(LeakServers.echoableToolCall(), server.http)).isEmpty();
        assertThat(server.requestsSeen()).isGreaterThan(2 + LeakTrial.CLIENTS);
    }

    @Test
    void distinctIdRoundsAnsweredWithoutTheCallersMarkersDoNotConfirm() {
        HostAudits check = audits();
        LeakServers.Server server = LeakServers.misroutingCollidingIdsAndAnsweringDistinctIdsWithoutMarkers();

        assertThat(check.audit(LeakServers.echoableToolCall(), server.http)).isEmpty();
        int afterFirst = server.requestsSeen();
        check.audit(LeakServers.echoableToolCall(), server.http);
        assertThat(server.requestsSeen()).isGreaterThan(afterFirst);
    }

    @Test
    void sharedStateThatCrossesIntermittentlyIsSilent() {
        LeakServers.Server server = LeakServers.rotatingOnlyTheFirstRace(false);

        assertThat(check().audit(LeakServers.echoableToolCall(), server.http)).isEmpty();
    }

    @Test
    void distinctIdRoundsThatGoUnansweredDoNotConfirm() {
        LeakServers.Server server = LeakServers.rotatingOnlyTheFirstRace(true);

        assertThat(check().audit(LeakServers.echoableToolCall(), server.http)).isEmpty();
    }

    @Test
    void neverRacesAgainAHostThatCrossesWhateverTheRequestIds() {
        HostAudits check = audits();
        LeakServers.Server server = LeakServers.rotatingRepliesAmongConcurrentCallers();
        check.audit(LeakServers.echoableToolCall(), server.http);
        int afterFirst = server.requestsSeen();

        check.audit(LeakServers.echoableToolCall(), server.http);

        assertThat(server.requestsSeen()).isEqualTo(afterFirst);
    }

    @Test
    void stopsCalibratingAHostAfterTooManyUnsuitableCalls() {
        HostAudits check = audits();
        LeakServers.Server notEchoing = LeakServers.notEchoing();
        for (int seed = 0; seed < HostAudits.MAX_UNSUITABLE_SEEDS + 3; seed++) {
            check.audit(LeakServers.echoableToolCall(), notEchoing.http);
        }

        assertThat(notEchoing.requestsSeen()).isEqualTo(HostAudits.MAX_UNSUITABLE_SEEDS);
    }

    @Test
    void stopsRacingARateLimitedHost() {
        HostAudits check = audits();
        LeakServers.Server server = LeakServers.rateLimitingTheRace();
        check.audit(LeakServers.echoableToolCall(), server.http);
        int afterFirst = server.requestsSeen();

        check.audit(LeakServers.echoableToolCall(), server.http);

        assertThat(afterFirst).isEqualTo(2 + LeakTrial.CLIENTS);
        assertThat(server.requestsSeen()).isEqualTo(afterFirst);
    }

    @Test
    void boundsHowLongEachAttemptWaitsForStalledReplies() {
        try (LeakServers.Server server = LeakServers.stallingTheRace(Duration.ofSeconds(5))) {
            HostAudits check = new HostAudits(new CrossClientResponseLeakCheck(McpEventLog.noop(),
                    NoAuthStrategy::new, () -> probeService, Duration.ofMillis(200)));
            long started = System.nanoTime();

            check.audit(LeakServers.echoableToolCall(), server.http);

            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(12));
            int afterFirst = server.requestsSeen();
            check.audit(LeakServers.echoableToolCall(), server.http);
            assertThat(server.requestsSeen()).isGreaterThan(afterFirst);
        }
    }

    @Test
    void closesEveryProbeSessionAfterAFullTrial() {
        LeakServers.Server server = LeakServers.echoing();

        check().audit(LeakServers.echoableToolCall(), server.http);

        assertThat(server.sessionsOpened()).isEqualTo(LeakTrial.CLIENTS);
        assertThat(server.sessionsDeleted()).isEqualTo(server.sessionsOpened());
    }

    @Test
    void closesTheProbeSessionsOfATrialThatStopsAtTheSequentialControl() {
        LeakServers.Server server = LeakServers.rememberingEveryCaller();

        check().audit(LeakServers.echoableToolCall(), server.http);

        assertThat(server.sessionsOpened()).isEqualTo(2);
        assertThat(server.sessionsDeleted()).isEqualTo(2);
    }

    @Test
    void closesTheProbeSessionOfACallThatDoesNotEcho() {
        LeakServers.Server server = LeakServers.notEchoing();

        check().audit(LeakServers.echoableToolCall(), server.http);

        assertThat(server.sessionsOpened()).isEqualTo(1);
        assertThat(server.sessionsDeleted()).isEqualTo(1);
    }

    @Test
    void closesTheSessionsAlreadyOpenedWhenALaterHandshakeIsRefused() {
        LeakServers.Server server = LeakServers.echoing().acceptingHandshakes(4);

        check().audit(LeakServers.echoableToolCall(), server.http);

        assertThat(server.sessionsOpened()).isEqualTo(4);
        assertThat(server.sessionsDeleted()).isEqualTo(4);
        assertThat(server.requestsSeen()).isEqualTo(2);
    }

    @Test
    void logsWhyAProbeSessionCouldNotBeOpened() {
        McpEventLog eventLog = new McpEventLog(null);
        LeakServers.Server server = LeakServers.refusingHandshakes();

        check(eventLog).audit(LeakServers.echoableToolCall(), server.http);

        assertThat(eventLog.snapshot()).extracting(McpEventLog.LogEntry::message)
                .anySatisfy(message -> assertThat(message).contains("probe session").contains("refused"));
    }

    @Test
    void staysSilentWhenEveryClientGetsItsOwnReply() {
        LeakServers.Server server = LeakServers.echoing();

        assertThat(check().audit(LeakServers.echoableToolCall(), server.http)).isEmpty();
        assertThat(server.requestsSeen()).isEqualTo(FULL_ROUND_WITHOUT_CROSSING);
    }

    @Test
    void doesNotRaceAServerThatSharesStateBetweenSequentialClients() {
        LeakServers.Server server = LeakServers.rememberingEveryCaller();

        assertThat(check().audit(LeakServers.echoableToolCall(), server.http)).isEmpty();
        assertThat(server.requestsSeen()).isEqualTo(2);
    }

    @Test
    void doesNotRaceAToolThatNeverEchoesItsArguments() {
        LeakServers.Server server = LeakServers.notEchoing();

        assertThat(check().audit(LeakServers.echoableToolCall(), server.http)).isEmpty();
        assertThat(server.requestsSeen()).isEqualTo(1);
    }

    @Test
    void findsTheEchoedArgumentAmongSeveral() {
        LeakServers.Server server = LeakServers.misroutingByRequestId();

        List<AuditIssue> issues = check().audit(
                LeakServers.toolCall("{\"mode\":\"fast\",\"" + LeakServers.ECHOED_ARGUMENT + "\":\"hello\"}"),
                server.http);

        assertThat(issues).extracting(AuditIssue::name).containsExactly(ISSUE);
    }

    @Test
    void ignoresCallsWithoutAStringArgument() {
        LeakServers.Server server = LeakServers.misroutingByRequestId();

        assertThat(check().audit(LeakServers.toolCall("{\"count\":3}"), server.http)).isEmpty();
        assertThat(server.requestsSeen()).isZero();
    }

    @Test
    void skipsTheLegacySseTransport() {
        probeService = Optional.of(HttpService.httpService("127.0.0.1", 9999, false));
        McpEventLog eventLog = new McpEventLog(null);
        LeakServers.Server server = LeakServers.misroutingByRequestId();

        assertThat(audits(eventLog).audit(LeakServers.echoableToolCall(), server.http)).isEmpty();
        assertThat(server.requestsSeen()).isZero();
        assertThat(eventLog.snapshot()).extracting(McpEventLog.LogEntry::message)
                .contains(PREFIX + "skipped tools/call note at " + LeakServers.URL
                        + " (not applicable: the legacy SSE transport has no Streamable HTTP probe sessions)");
    }

    @Test
    void citesTheRobbedOwnersRequestAlthoughItNeverGotAResponse() {
        LeakServers.Server server = LeakServers.misroutingByRequestId();

        AuditIssue issue = check().audit(LeakServers.echoableToolCall(), server.http).get(0);

        HttpRequestResponse owner = issue.requestResponses().stream()
                .filter(exchange -> exchange.response() == null).findFirst().orElseThrow();
        HttpRequestResponse recipient = issue.requestResponses().stream()
                .filter(exchange -> exchange.response() != null).findFirst().orElseThrow();
        String ownerMarker = LeakServers.markerIn(owner.request().bodyToString());
        assertThat(recipient.response().bodyToString()).contains(ownerMarker);
    }

    @Test
    void neverProbesALiveSseMessageEndpoint() {
        LeakServers.Server server = LeakServers.misroutingByRequestId();

        List<AuditIssue> issues = check().audit(LeakServers.toolCall("http://target.test/messages/?session_id=live",
                "{\"" + LeakServers.ECHOED_ARGUMENT + "\":\"hello\"}"), server.http);

        assertThat(issues).isEmpty();
        assertThat(server.requestsSeen()).isZero();
    }

    @Test
    void racesEachHostOnceAfterAFullTrial() {
        HostAudits check = audits();
        LeakServers.Server server = LeakServers.echoing();
        check.audit(LeakServers.echoableToolCall(), server.http);
        int afterFirst = server.requestsSeen();

        check.audit(LeakServers.echoableToolCall(), server.http);

        assertThat(server.requestsSeen()).isEqualTo(afterFirst);
    }

    @Test
    void triesAnotherCallWhenTheFirstDoesNotEcho() {
        HostAudits check = audits();
        check.audit(LeakServers.echoableToolCall(), LeakServers.notEchoing().http);

        List<AuditIssue> issues = check.audit(LeakServers.echoableToolCall(),
                LeakServers.misroutingByRequestId().http);

        assertThat(issues).extracting(AuditIssue::name).containsExactly(ISSUE);
    }

    @Test
    void keepsTryingCallsWhileNoneEchoes() {
        HostAudits check = audits();
        LeakServers.Server notEchoing = LeakServers.notEchoing();
        for (int seed = 0; seed < HostAudits.MAX_INCONCLUSIVE_ATTEMPTS + 2; seed++) {
            check.audit(LeakServers.echoableToolCall(), notEchoing.http);
        }

        List<AuditIssue> issues = check.audit(LeakServers.echoableToolCall(),
                LeakServers.misroutingByRequestId().http);

        assertThat(issues).extracting(AuditIssue::name).containsExactly(ISSUE);
    }

    @Test
    void racesTheHostAgainWhenConcurrentCallsWentUnanswered() {
        HostAudits check = audits();
        LeakServers.Server refusing = LeakServers.refusingTheRace();
        check.audit(LeakServers.echoableToolCall(), refusing.http);
        int afterFirst = refusing.requestsSeen();

        check.audit(LeakServers.echoableToolCall(), refusing.http);

        assertThat(refusing.requestsSeen()).isGreaterThan(afterFirst);
    }

    @Test
    void doesNotRaceWhenTheSecondClientsMarkerIsNotEchoed() {
        LeakServers.Server server = LeakServers.echoingOnlyTheFirstCall();

        assertThat(check().audit(LeakServers.echoableToolCall(), server.http)).isEmpty();
        assertThat(server.requestsSeen()).isEqualTo(2);
    }

    @Test
    void forgetsSettledHostsOnDisconnect() {
        HostAudits check = audits();
        LeakServers.Server server = LeakServers.echoing();
        check.audit(LeakServers.echoableToolCall(), server.http);
        int afterFirst = server.requestsSeen();

        check.clear();
        check.audit(LeakServers.echoableToolCall(), server.http);

        assertThat(server.requestsSeen()).isGreaterThan(afterFirst);
    }

    @Test
    void mapsEveryTrialVerdictToAHostSettlement() {
        assertThat(Arrays.stream(TrialOutcome.Verdict.values())
                .collect(Collectors.toMap(verdict -> verdict, TrialOutcome.Verdict::settlement)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        TrialOutcome.Verdict.NO_PROBE_SESSION, HostSettlement.INCONCLUSIVE,
                        TrialOutcome.Verdict.MODERN_ONLY_SERVER, HostSettlement.SETTLED,
                        TrialOutcome.Verdict.NO_ECHOED_ARGUMENT, HostSettlement.UNSUITABLE_SEED,
                        TrialOutcome.Verdict.STATE_SHARED_BETWEEN_SEQUENTIAL_CLIENTS, HostSettlement.UNSUITABLE_SEED,
                        TrialOutcome.Verdict.STATE_SHARED_UNDER_CONCURRENCY, HostSettlement.SETTLED,
                        TrialOutcome.Verdict.INTERMITTENT_CROSSING, HostSettlement.SETTLED,
                        TrialOutcome.Verdict.UNANSWERED_RACE, HostSettlement.INCONCLUSIVE,
                        TrialOutcome.Verdict.RATE_LIMITED, HostSettlement.SETTLED,
                        TrialOutcome.Verdict.NO_CROSSING, HostSettlement.SETTLED,
                        TrialOutcome.Verdict.CROSSED, HostSettlement.SETTLED));
    }

    @Test
    void aCrossingSettlesTheHost() {
        assertThat(settlementAgainst(LeakServers.misroutingByRequestId())).isEqualTo(HostSettlement.SETTLED);
    }

    @Test
    void aCallThatDoesNotEchoIsAnUnsuitableSeed() {
        assertThat(settlementAgainst(LeakServers.notEchoing())).isEqualTo(HostSettlement.UNSUITABLE_SEED);
    }

    @Test
    void stateSharedBetweenSequentialClientsIsAnUnsuitableSeed() {
        assertThat(settlementAgainst(LeakServers.rememberingEveryCaller())).isEqualTo(HostSettlement.UNSUITABLE_SEED);
    }

    @Test
    void anUnansweredRaceIsInconclusive() {
        assertThat(settlementAgainst(LeakServers.refusingTheRace())).isEqualTo(HostSettlement.INCONCLUSIVE);
    }

    @Test
    void aRefusedProbeSessionIsInconclusive() {
        assertThat(settlementAgainst(LeakServers.refusingHandshakes())).isEqualTo(HostSettlement.INCONCLUSIVE);
    }

    @Test
    void aModernOnlyServerRefusingTheHandshakeSettlesTheHostUnreported() {
        LeakServers.Server server = LeakServers.refusingHandshakes().refusingHandshakesWith(400);

        HostTrialResult result = check().trials().get(0).run(LeakServers.modernEchoableToolCall(), server.http);

        assertThat(result.issues()).isEmpty();
        assertThat(result.settlement()).isEqualTo(HostSettlement.SETTLED);
    }

    @Test
    void aModernBaseOnAServerRefusingOnlyALaterHandshakeIsInconclusive() {
        LeakServers.Server server = LeakServers.echoing().acceptingHandshakes(2).refusingHandshakesWith(400);

        assertThat(settlementAgainst(LeakServers.modernEchoableToolCall(), server))
                .isEqualTo(HostSettlement.INCONCLUSIVE);
    }

    @Test
    void aModernBaseWhoseInitializedNotificationIsRefusedIsInconclusive() {
        ScriptedBurpHttp refusingInitialized = new ScriptedBurpHttp(request ->
                Optional.of("notifications/initialized".equals(FakeMcpServer.method(request))
                        ? FakeMcpServer.exchange(request, 400, Map.of(), "bad request")
                        : FakeMcpServer.respond(request)));

        assertThat(check().trials().get(0).run(LeakServers.modernEchoableToolCall(), refusingInitialized).settlement())
                .isEqualTo(HostSettlement.INCONCLUSIVE);
    }

    @Test
    void aModernBaseWhoseHandshakeNeedsCredentialsIsInconclusive() {
        LeakServers.Server server = LeakServers.refusingHandshakes().refusingHandshakesWith(403);

        assertThat(settlementAgainst(LeakServers.modernEchoableToolCall(), server))
                .isEqualTo(HostSettlement.INCONCLUSIVE);
    }

    @Test
    void anInitializeBasedBaseWhoseHandshakeIsRefusedIsInconclusive() {
        LeakServers.Server server = LeakServers.refusingHandshakes().refusingHandshakesWith(400);

        assertThat(settlementAgainst(LeakServers.echoableToolCall(), server)).isEqualTo(HostSettlement.INCONCLUSIVE);
    }

    @Test
    void aModernBaseWhoseHandshakeGoesUnansweredIsInconclusive() {
        ScriptedBurpHttp unanswering = new ScriptedBurpHttp(request -> Optional.empty());

        assertThat(check().trials().get(0).run(LeakServers.modernEchoableToolCall(), unanswering).settlement())
                .isEqualTo(HostSettlement.INCONCLUSIVE);
    }

    @Test
    void aRateLimitedRaceSettlesTheHost() {
        assertThat(settlementAgainst(LeakServers.rateLimitingTheRace())).isEqualTo(HostSettlement.SETTLED);
    }

    @Test
    void appliesOnlyToEchoableCallsOffTheLegacySseTransport() {
        assertThat(check().appliesTo(LeakServers.echoableToolCall())).isTrue();
        assertThat(check().appliesTo(LeakServers.toolCall("{\"count\":3}"))).isFalse();

        probeService = Optional.of(HttpService.httpService("127.0.0.1", 9999, false));

        assertThat(check().appliesTo(LeakServers.echoableToolCall())).isFalse();
    }

    @Test
    void theTrialKeepsItsHostClaimedWhenItThrows() {
        assertThat(check().trials().get(0).settlementOnFailure()).isEqualTo(HostSettlement.SETTLED);
    }

    private HostSettlement settlementAgainst(LeakServers.Server server) {
        return settlementAgainst(LeakServers.echoableToolCall(), server);
    }

    private HostSettlement settlementAgainst(HttpRequestResponse base, LeakServers.Server server) {
        return check().trials().get(0).run(base, server.http).settlement();
    }

    private HostAudits audits() {
        return new HostAudits(check());
    }

    private CrossClientResponseLeakCheck check() {
        return check(McpEventLog.noop());
    }

    @Test
    void logsWhyACallWithoutAStringArgumentIsSkipped() {
        assertThat(logOf(LeakServers.toolCall("{\"count\":3}"), LeakServers.echoing())).contains(PREFIX
                + "skipped tools/call note at " + LeakServers.URL
                + " (not applicable: needs a tools/call with a string argument to echo)");
    }

    @Test
    void logsThatAHostAlreadyRacedIsSkipped() {
        McpEventLog eventLog = new McpEventLog(null);
        HostAudits check = audits(eventLog);
        LeakServers.Server server = LeakServers.echoing();
        check.audit(LeakServers.echoableToolCall(), server.http);

        check.audit(LeakServers.echoableToolCall(), server.http);

        assertThat(messages(eventLog)).contains(PREFIX + "skipped tools/call note at " + LeakServers.URL
                + " (already audited on this connection: this host was already probed)");
    }

    @Test
    void logsThatAHostOutOfUnsuitableSeedsIsSkipped() {
        McpEventLog eventLog = new McpEventLog(null);
        HostAudits check = audits(eventLog);
        LeakServers.Server notEchoing = LeakServers.notEchoing();
        for (int seed = 0; seed <= HostAudits.MAX_UNSUITABLE_SEEDS; seed++) {
            check.audit(LeakServers.echoableToolCall(), notEchoing.http);
        }

        assertThat(messages(eventLog)).contains(PREFIX + "stopped tools/call note at " + LeakServers.URL
                + " (request budget used up: " + HostAudits.MAX_UNSUITABLE_SEEDS
                + " unsuitable requests on this host)");
    }

    @Test
    void logsThatACallWhoseArgumentsAreNotEchoedStopsBeforeRacing() {
        assertThat(logOf(LeakServers.echoableToolCall(), LeakServers.notEchoing())).contains(PREFIX
                + "skipped tools/call note at " + LeakServers.URL
                + " (prerequisite missing: the server echoes none of the call's string arguments)");
    }

    @Test
    void logsThatARateLimitedRaceStopped() {
        assertThat(logOf(LeakServers.echoableToolCall(), LeakServers.rateLimitingTheRace())).contains(PREFIX
                + "stopped tools/call note at " + LeakServers.URL + " (rate limited: the server answered HTTP 429)");
    }

    @Test
    void stopsRacingAHostThatRateLimitsTheProbeHandshake() {
        McpEventLog eventLog = new McpEventLog(null);
        HostAudits check = audits(eventLog);
        LeakServers.Server server = LeakServers.rateLimitingHandshakes();
        check.audit(LeakServers.echoableToolCall(), server.http);

        check.audit(LeakServers.echoableToolCall(), server.http);

        assertThat(server.sessionsOpened()).isZero();
        assertThat(messages(eventLog)).contains(PREFIX + "stopped tools/call note at " + LeakServers.URL
                + " (rate limited: the server answered HTTP 429)");
    }

    @Test
    void logsThatATrialWithoutAProbeSessionStopped() {
        assertThat(logOf(LeakServers.echoableToolCall(), LeakServers.refusingHandshakes())).contains(PREFIX
                + "skipped tools/call note at " + LeakServers.URL
                + " (prerequisite missing: no probe session could be opened)");
    }

    @Test
    void logsThatAModernOnlyServerIsSkippedAndNotRetried() {
        McpEventLog eventLog = new McpEventLog(null);
        HostAudits check = audits(eventLog);
        LeakServers.Server server = LeakServers.refusingHandshakes().refusingHandshakesWith(400);
        check.audit(LeakServers.modernEchoableToolCall(), server.http);

        check.audit(LeakServers.modernEchoableToolCall(), server.http);

        assertThat(messages(eventLog)).contains(PREFIX + "skipped tools/call note at " + LeakServers.URL
                + " (not applicable: the server serves only MCP 2026-07-28 and refuses the initialize handshake,"
                + " so no separate probe sessions can be opened)");
        assertThat(messages(eventLog)).contains(PREFIX + "skipped tools/call note at " + LeakServers.URL
                + " (already audited on this connection: this host was already probed)");
    }

    @Test
    void logsThatARaceWhoseRoundsWentUnansweredStopped() {
        assertThat(logOf(LeakServers.echoableToolCall(), LeakServers.refusingTheRace())).contains(PREFIX
                + "stopped tools/call note at " + LeakServers.URL
                + " (transport failure: not every probe client got a successful reply to a race round in time)");
    }

    private List<String> logOf(HttpRequestResponse base, LeakServers.Server server) {
        McpEventLog eventLog = new McpEventLog(null);
        audits(eventLog).audit(base, server.http);
        return messages(eventLog);
    }

    private static List<String> messages(McpEventLog eventLog) {
        return eventLog.snapshot().stream().map(McpEventLog.LogEntry::message).toList();
    }

    private HostAudits audits(McpEventLog eventLog) {
        CrossClientResponseLeakCheck check = check(eventLog);
        return new HostAudits(check, new CheckLog(eventLog, check.descriptor().id()));
    }

    private CrossClientResponseLeakCheck check(McpEventLog eventLog) {
        return new CrossClientResponseLeakCheck(eventLog, NoAuthStrategy::new, () -> probeService, PROBE_TIMEOUT);
    }
}
