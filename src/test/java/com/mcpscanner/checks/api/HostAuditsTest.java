package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostAuditsTest {

    private static final String PREFIX = "Scan check: retrying — ";
    private static final String SUBJECT = "a base request without a request";

    private final Http http = mock(Http.class);
    private final AuditIssue issue = mock(AuditIssue.class);
    private final AuditIssue otherIssue = mock(AuditIssue.class);
    private final HttpRequestResponse onHostA = base("a.test");
    private final HttpRequestResponse onHostB = base("b.test");

    @Test
    void aSettledTrialKeepsItsHostClaimed() {
        ScriptedTrial trial = new ScriptedTrial(HostSettlement.SETTLED, List.of(issue));
        HostAudits audits = auditsOf(trial);

        assertThat(audits.audit(onHostA, http)).containsExactly(issue);
        assertThat(audits.audit(base("A.test"), http)).isEmpty();
        assertThat(audits.audit(onHostB, http)).containsExactly(issue);
        assertThat(trial.runs).containsExactly(onHostA, onHostB);
    }

    @Test
    void aBaseRequestTheTrialDoesNotApplyToLeavesTheHostUnclaimed() {
        HttpRequestResponse applicable = base("a.test");
        ScriptedTrial trial = new ScriptedTrial(HostSettlement.SETTLED, List.of(issue), candidate -> candidate == applicable);
        HostAudits audits = auditsOf(trial);

        assertThat(audits.audit(onHostA, http)).isEmpty();
        assertThat(audits.audit(applicable, http)).containsExactly(issue);
        assertThat(trial.runs).containsExactly(applicable);
    }

    @Test
    void anUnprobedTrialIsReleasedWithoutUsingUpAnyLimit() {
        ScriptedTrial trial = ScriptedTrial.settling(HostSettlement.NOT_PROBED);
        HostAudits audits = auditsOf(trial);

        auditRepeatedly(audits, HostAudits.MAX_UNSUITABLE_SEEDS + 5);

        assertThat(trial.runs).hasSize(HostAudits.MAX_UNSUITABLE_SEEDS + 5);
    }

    @Test
    void anInconclusiveTrialIsRetriedUntilTheAttemptLimit() {
        ScriptedTrial trial = ScriptedTrial.settling(HostSettlement.INCONCLUSIVE);
        HostAudits audits = auditsOf(trial);

        auditRepeatedly(audits, HostAudits.MAX_INCONCLUSIVE_ATTEMPTS + 2);

        assertThat(trial.runs).hasSize(HostAudits.MAX_INCONCLUSIVE_ATTEMPTS);
    }

    @Test
    void anUnsuitableSeedIsRetriedUntilTheSeedLimit() {
        ScriptedTrial trial = ScriptedTrial.settling(HostSettlement.UNSUITABLE_SEED);
        HostAudits audits = auditsOf(trial);

        auditRepeatedly(audits, HostAudits.MAX_UNSUITABLE_SEEDS + 2);

        assertThat(trial.runs).hasSize(HostAudits.MAX_UNSUITABLE_SEEDS);
    }

    @Test
    void inconclusiveAttemptsAndUnsuitableSeedsAreCountedSeparately() {
        AlternatingTrial trial = new AlternatingTrial(HostSettlement.INCONCLUSIVE, HostSettlement.UNSUITABLE_SEED);
        HostAudits audits = new HostAudits(new ScriptedTrial.Check(trial));

        auditRepeatedly(audits, 2 * HostAudits.MAX_INCONCLUSIVE_ATTEMPTS + 3);

        assertThat(trial.runs).isEqualTo(2 * (HostAudits.MAX_INCONCLUSIVE_ATTEMPTS - 1) + 1);
    }

    @Test
    void limitsAreCountedPerHost() {
        ScriptedTrial trial = ScriptedTrial.settling(HostSettlement.INCONCLUSIVE);
        HostAudits audits = auditsOf(trial);
        auditRepeatedly(audits, HostAudits.MAX_INCONCLUSIVE_ATTEMPTS);

        audits.audit(onHostB, http);

        assertThat(trial.runs).last().isEqualTo(onHostB);
    }

    @Test
    void eachTrialHasItsOwnClaimOnTheHost() {
        ScriptedTrial settled = new ScriptedTrial(HostSettlement.SETTLED, List.of(issue));
        ScriptedTrial inconclusive = new ScriptedTrial(HostSettlement.INCONCLUSIVE, List.of(otherIssue));
        HostAudits audits = auditsOf(settled, inconclusive);

        List<AuditIssue> first = audits.audit(onHostA, http);
        List<AuditIssue> second = audits.audit(onHostA, http);

        assertThat(first).containsExactly(issue, otherIssue);
        assertThat(second).containsExactly(otherIssue);
        assertThat(settled.runs).hasSize(1);
        assertThat(inconclusive.runs).hasSize(2);
    }

    @Test
    void aThrowingTrialReleasesItsClaimUncountedAndRethrows() {
        FailingTrial trial = new FailingTrial();
        HostAudits audits = auditsOf(trial);

        for (int attempt = 0; attempt < HostAudits.MAX_UNSUITABLE_SEEDS + 2; attempt++) {
            assertThatThrownBy(() -> audits.audit(onHostA, http)).isInstanceOf(IllegalStateException.class);
        }

        assertThat(trial.attempts).isEqualTo(HostAudits.MAX_UNSUITABLE_SEEDS + 2);
    }

    @Test
    void aThrowingTrialThatSettlesOnFailureKeepsItsClaimAndRethrowsOnce() {
        FailingTrial trial = new FailingTrialSettledByFailure();
        HostAudits audits = auditsOf(trial);

        assertThatThrownBy(() -> audits.audit(onHostA, http)).isInstanceOf(IllegalStateException.class);
        assertThat(audits.audit(onHostA, http)).isEmpty();

        assertThat(trial.attempts).isEqualTo(1);
    }

    @Test
    void aTrialSettledByFailureLeavesLaterTrialsFreeToRunOnLaterRequests() {
        FailingTrial failing = new FailingTrialSettledByFailure();
        ScriptedTrial later = ScriptedTrial.settling(HostSettlement.INCONCLUSIVE);
        HostAudits audits = auditsOf(failing, later);

        assertThatThrownBy(() -> audits.audit(onHostA, http)).isInstanceOf(IllegalStateException.class);
        audits.audit(onHostA, http);
        audits.audit(onHostA, http);

        assertThat(failing.attempts).isEqualTo(1);
        assertThat(later.runs).hasSize(2);
    }

    @Test
    void clearingForgetsClaimsAndCounters() {
        ScriptedTrial settled = ScriptedTrial.settling(HostSettlement.SETTLED);
        ScriptedTrial inconclusive = ScriptedTrial.settling(HostSettlement.INCONCLUSIVE);
        HostAudits audits = auditsOf(settled, inconclusive);
        auditRepeatedly(audits, HostAudits.MAX_INCONCLUSIVE_ATTEMPTS);

        audits.clear();
        audits.audit(onHostA, http);

        assertThat(settled.runs).hasSize(2);
        assertThat(inconclusive.runs).hasSize(HostAudits.MAX_INCONCLUSIVE_ATTEMPTS + 1);
    }

    @Test
    void aTrialRunningOnOneThreadIsNotStartedOnAnotherForTheSameHost() throws Exception {
        BlockingTrial trial = new BlockingTrial();
        HostAudits audits = auditsOf(trial);
        CompletableFuture<List<AuditIssue>> running = CompletableFuture.supplyAsync(() -> audits.audit(onHostA, http));
        assertThat(trial.started.await(5, TimeUnit.SECONDS)).isTrue();

        List<AuditIssue> concurrent = audits.audit(base("a.test"), http);
        List<AuditIssue> otherHost = audits.audit(onHostB, http);
        trial.finish.countDown();

        assertThat(concurrent).isEmpty();
        assertThat(otherHost).containsExactly(issue);
        assertThat(running.get(5, TimeUnit.SECONDS)).containsExactly(issue);
        assertThat(trial.runs).containsExactly(onHostA, onHostB);
    }

    @Test
    void logsThatAHostWhoseTrialIsSettledIsSkipped() {
        McpEventLog eventLog = new McpEventLog(null);
        HostAudits audits = loggedAuditsOf(eventLog, ScriptedTrial.settling(HostSettlement.SETTLED));

        audits.audit(onHostA, http);
        audits.audit(onHostA, http);

        assertThat(messages(eventLog)).containsExactly(PREFIX + "skipped " + SUBJECT
                + " (already audited on this connection: this host was already probed)");
    }

    @Test
    void logsThatAHostOutOfInconclusiveAttemptsIsStopped() {
        McpEventLog eventLog = new McpEventLog(null);
        HostAudits audits = loggedAuditsOf(eventLog, ScriptedTrial.settling(HostSettlement.INCONCLUSIVE));

        auditRepeatedly(audits, HostAudits.MAX_INCONCLUSIVE_ATTEMPTS + 1);

        assertThat(messages(eventLog)).containsExactly(PREFIX + "stopped " + SUBJECT + " (request budget used up: "
                + HostAudits.MAX_INCONCLUSIVE_ATTEMPTS + " inconclusive attempts on this host)");
    }

    @Test
    void logsThatAHostOutOfUnsuitableSeedsIsStopped() {
        McpEventLog eventLog = new McpEventLog(null);
        HostAudits audits = loggedAuditsOf(eventLog, ScriptedTrial.settling(HostSettlement.UNSUITABLE_SEED));

        auditRepeatedly(audits, HostAudits.MAX_UNSUITABLE_SEEDS + 1);

        assertThat(messages(eventLog)).containsExactly(PREFIX + "stopped " + SUBJECT + " (request budget used up: "
                + HostAudits.MAX_UNSUITABLE_SEEDS + " unsuitable requests on this host)");
    }

    @Test
    void logsARefusedClaimUnderTheTrialsOwnSubject() {
        McpEventLog eventLog = new McpEventLog(null);
        HostAudits audits = loggedAuditsOf(eventLog, new NamedTrial("the named test"));

        audits.audit(onHostA, http);
        audits.audit(onHostA, http);

        assertThat(messages(eventLog)).containsExactly(PREFIX + "skipped the named test "
                + "(already audited on this connection: this host was already probed)");
    }

    @Test
    void logsWhyATrialIsSkippedWhileAnotherRuns() {
        McpEventLog eventLog = new McpEventLog(null);
        ScriptedTrial runs = ScriptedTrial.settling(HostSettlement.INCONCLUSIVE);
        HostAudits audits = loggedAuditsOf(eventLog, runs,
                new ScriptedTrial(HostSettlement.SETTLED, List.of(), base -> false));

        audits.audit(onHostA, http);

        assertThat(runs.runs).containsExactly(onHostA);
        assertThat(messages(eventLog)).containsExactly(PREFIX + "skipped " + SUBJECT + " ("
                + ScriptedTrial.NOT_APPLICABLE.text() + ")");
    }

    @Test
    void logsOnceWhyNoTrialApplies() {
        McpEventLog eventLog = new McpEventLog(null);
        HostAudits audits = loggedAuditsOf(eventLog, new ScriptedTrial(HostSettlement.SETTLED, List.of(), base -> false),
                new ScriptedTrial(HostSettlement.SETTLED, List.of(), base -> false));

        audits.audit(onHostA, http);

        assertThat(messages(eventLog)).containsExactly(PREFIX + "skipped " + SUBJECT + " ("
                + ScriptedTrial.NOT_APPLICABLE.text() + ")");
    }

    @Test
    void namesTheFirstTrialsReasonOnlyWhenNoTrialApplies() {
        ScriptedTrial skipping = new ScriptedTrial(HostSettlement.SETTLED, List.of(), base -> false);

        assertThat(auditsOf(skipping, skipping).whyNotApplicable(onHostA)).contains(ScriptedTrial.NOT_APPLICABLE);
        assertThat(auditsOf(skipping, ScriptedTrial.settling(HostSettlement.SETTLED)).whyNotApplicable(onHostA))
                .isEmpty();
    }

    private HostAudits loggedAuditsOf(McpEventLog eventLog, HostTrial... trials) {
        ScriptedTrial.Check check = new ScriptedTrial.Check(trials);
        return new HostAudits(check, new CheckLog(eventLog, check.descriptor().id()));
    }

    private static List<String> messages(McpEventLog eventLog) {
        return eventLog.snapshot().stream().map(McpEventLog.LogEntry::message).toList();
    }

    private HostAudits auditsOf(HostTrial... trials) {
        return new HostAudits(new ScriptedTrial.Check(trials));
    }

    private void auditRepeatedly(HostAudits audits, int times) {
        for (int attempt = 0; attempt < times; attempt++) {
            audits.audit(onHostA, http);
        }
    }

    private static HttpRequestResponse base(String host) {
        HttpService service = HostKeyTest.service(host, 443, true);
        HttpRequestResponse base = mock(HttpRequestResponse.class);
        when(base.httpService()).thenReturn(service);
        return base;
    }

    private static final class NamedTrial extends ScriptedTrial {

        private final String subject;

        private NamedTrial(String subject) {
            super(HostSettlement.SETTLED, List.of());
            this.subject = subject;
        }

        @Override
        public String subject(HttpRequestResponse base) {
            return subject;
        }
    }

    private static final class AlternatingTrial implements HostTrial {

        private final List<HostSettlement> settlements;
        private int runs;

        private AlternatingTrial(HostSettlement... settlements) {
            this.settlements = List.of(settlements);
        }

        @Override
        public Optional<SkipReason> whyNotApplicable(HttpRequestResponse base) {
            return Optional.empty();
        }

        @Override
        public HostTrialResult run(HttpRequestResponse base, Http http) {
            return new HostTrialResult(List.of(), settlements.get(runs++ % settlements.size()));
        }
    }

    private static class FailingTrial implements HostTrial {

        private int attempts;

        @Override
        public Optional<SkipReason> whyNotApplicable(HttpRequestResponse base) {
            return Optional.empty();
        }

        @Override
        public HostTrialResult run(HttpRequestResponse base, Http http) {
            attempts++;
            throw new IllegalStateException("transient");
        }
    }

    private static final class FailingTrialSettledByFailure extends FailingTrial {

        @Override
        public HostSettlement settlementOnFailure() {
            return HostSettlement.SETTLED;
        }
    }

    private final class BlockingTrial extends ScriptedTrial {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch finish = new CountDownLatch(1);

        private BlockingTrial() {
            super(HostSettlement.SETTLED, List.of(issue));
        }

        @Override
        public HostTrialResult run(HttpRequestResponse base, Http http) {
            HostTrialResult result = super.run(base, http);
            if (base == onHostA) {
                started.countDown();
                awaitFinish();
            }
            return result;
        }

        private void awaitFinish() {
            try {
                finish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
