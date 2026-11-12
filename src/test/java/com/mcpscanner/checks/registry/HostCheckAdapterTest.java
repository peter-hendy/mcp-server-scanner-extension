package com.mcpscanner.checks.registry;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.scancheck.ScanCheckType;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.HostCheck;
import com.mcpscanner.checks.api.RecordingChecks;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class HostCheckAdapterTest {

    private final AuditIssue issue = mock(AuditIssue.class);
    private final Http http = mock(Http.class);
    private final HttpRequestResponse firstOnHostA = AdapterFixtures.baseOn("a.test");
    private final HttpRequestResponse secondOnHostA = AdapterFixtures.baseOn("A.test");
    private final HttpRequestResponse onHostB = AdapterFixtures.baseOn("b.test");

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void registersWithBurpPerRequestSoHostAuditsDecidesWhenAHostIsDone() {
        assertThat(AdapterFixtures.registeredScanCheckType(adapterFor(new RecordingChecks.Host("h", List.of()), true)))
                .isEqualTo(ScanCheckType.PER_REQUEST);
    }

    @Test
    void auditsEachHostOnlyOncePerSession() {
        RecordingChecks.Host check = new RecordingChecks.Host("h", List.of(issue));
        HostCheckAdapter adapter = adapterFor(check, true);

        List<AuditIssue> first = adapter.doCheck(firstOnHostA, null, http).auditIssues();
        List<AuditIssue> repeat = adapter.doCheck(secondOnHostA, null, http).auditIssues();
        List<AuditIssue> other = adapter.doCheck(onHostB, null, http).auditIssues();

        assertThat(first).containsExactly(issue);
        assertThat(repeat).isEmpty();
        assertThat(other).containsExactly(issue);
        assertThat(check.calls).extracting(RecordingChecks.Call::base).containsExactly(firstOnHostA, onHostB);
    }

    @Test
    void clearingSessionStateAllowsTheHostToBeAuditedAgain() {
        RecordingChecks.Host check = new RecordingChecks.Host("h", List.of(issue));
        HostCheckAdapter adapter = adapterFor(check, true);
        adapter.doCheck(firstOnHostA, null, http);

        adapter.clearConnectionState();

        assertThat(adapter.doCheck(firstOnHostA, null, http).auditIssues()).containsExactly(issue);
        assertThat(check.calls).hasSize(2);
    }

    @Test
    void disabledCheckNeitherRunsNorClaimsTheHost() {
        RecordingChecks.Host check = new RecordingChecks.Host("h", List.of(issue));
        ScanCheckSettings settings = AdapterFixtures.settingsAllowing(false);
        HostCheckAdapter adapter = new HostCheckAdapter(settings, McpEventLog.noop(), check);

        assertThat(adapter.doCheck(firstOnHostA, null, http).auditIssues()).isEmpty();
        assertThat(check.calls).isEmpty();
    }

    @Test
    void aFailedAuditReleasesTheHostSoALaterScanRetriesIt() {
        FlakyHostCheck check = new FlakyHostCheck(issue);
        HostCheckAdapter adapter = new HostCheckAdapter(AdapterFixtures.settingsAllowing(true), McpEventLog.noop(), check);

        assertThatThrownBy(() -> adapter.doCheck(firstOnHostA, null, http)).isInstanceOf(IllegalStateException.class);

        assertThat(adapter.doCheck(firstOnHostA, null, http).auditIssues()).containsExactly(issue);
        assertThat(check.attempts.get()).isEqualTo(2);
    }

    @Test
    void aBaseRequestTheCheckDoesNotApplyToLeavesTheHostUnclaimed() {
        HttpRequestResponse applicable = AdapterFixtures.baseOn("a.test");
        RecordingChecks.Host check = new RecordingChecks.Host("h", List.of(issue), base -> base == applicable);
        HostCheckAdapter adapter = adapterFor(check, true);

        List<AuditIssue> skipped = adapter.doCheck(firstOnHostA, null, http).auditIssues();
        List<AuditIssue> audited = adapter.doCheck(applicable, null, http).auditIssues();

        assertThat(skipped).isEmpty();
        assertThat(audited).containsExactly(issue);
        assertThat(check.calls).extracting(RecordingChecks.Call::base).containsExactly(applicable);
    }

    @Test
    void logsWhyAHostIsSkipped() {
        McpEventLog eventLog = new McpEventLog(null);
        RecordingChecks.Host check = new RecordingChecks.Host("h", List.of(), base -> base != onHostB);
        HostCheckAdapter adapter = new HostCheckAdapter(AdapterFixtures.settingsAllowing(true), eventLog, check);

        adapter.doCheck(firstOnHostA, null, http);
        adapter.doCheck(secondOnHostA, null, http);
        adapter.doCheck(onHostB, null, http);

        assertThat(eventLog.snapshot()).extracting(McpEventLog.LogEntry::message).contains(
                "Scan check: h — skipped a base request without a request "
                        + "(already audited on this connection: this host was already probed)",
                "Scan check: h — skipped a base request without a request "
                        + "(not applicable: applies only to recorded requests)");
    }

    @Test
    void rejectsACheckWhoseDescriptorDeclaresAnotherKind() {
        RecordingChecks.Host mislabelled = new RecordingChecks.Host(
                RecordingChecks.descriptorFor("h", CheckKind.REQUEST), List.of());

        assertThatThrownBy(() -> adapterFor(mislabelled, true)).isInstanceOf(IllegalArgumentException.class);
    }

    private static HostCheckAdapter adapterFor(RecordingChecks.Host check, boolean enabled) {
        return new HostCheckAdapter(AdapterFixtures.settingsAllowing(enabled), McpEventLog.noop(), check);
    }

    private static final class FlakyHostCheck implements HostCheck {

        private final AuditIssue issue;
        private final AtomicInteger attempts = new AtomicInteger();

        private FlakyHostCheck(AuditIssue issue) {
            this.issue = issue;
        }

        @Override
        public CheckDescriptor descriptor() {
            return RecordingChecks.descriptorFor("flaky", CheckKind.HOST);
        }

        @Override
        public boolean appliesTo(HttpRequestResponse hostBaseRequestResponse) {
            return true;
        }

        @Override
        public String appliesOnlyTo() {
            return "any request";
        }

        @Override
        public List<AuditIssue> audit(HttpRequestResponse hostBaseRequestResponse, Http http) {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("transient");
            }
            return List.of(issue);
        }
    }
}
