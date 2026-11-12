package com.mcpscanner.checks.registry;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.scancheck.ScanCheckType;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.RecordingChecks;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsertionPointCheckAdapterTest {

    private final AuditIssue issue = mock(AuditIssue.class);
    private final HttpRequestResponse base = mock(HttpRequestResponse.class);
    private final AuditInsertionPoint insertionPoint = mock(AuditInsertionPoint.class);
    private final Http http = mock(Http.class);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void registersWithBurpAsAPerInsertionPointCheck() {
        InsertionPointCheckAdapter adapter = adapterFor(new RecordingChecks.InsertionPoint("ip", List.of()), true);

        assertThat(AdapterFixtures.registeredScanCheckType(adapter)).isEqualTo(ScanCheckType.PER_INSERTION_POINT);
    }

    @Test
    void passesTheBaseRequestInsertionPointAndHttpToTheCheckAndReturnsItsIssues() {
        RecordingChecks.InsertionPoint check = new RecordingChecks.InsertionPoint("ip", List.of(issue));

        List<AuditIssue> issues = adapterFor(check, true).doCheck(base, insertionPoint, http).auditIssues();

        assertThat(issues).containsExactly(issue);
        assertThat(check.calls).singleElement().satisfies(call -> {
            assertThat(call.base()).isSameAs(base);
            assertThat(call.insertionPoint()).isSameAs(insertionPoint);
        });
    }

    @Test
    void disabledCheckIsNeverInvoked() {
        RecordingChecks.InsertionPoint check = new RecordingChecks.InsertionPoint("ip", List.of(issue));

        assertThat(adapterFor(check, false).doCheck(base, insertionPoint, http).auditIssues()).isEmpty();
        assertThat(check.calls).isEmpty();
    }

    @Test
    void exposesTheWrappedChecksDescriptorAndIssueName() {
        RecordingChecks.InsertionPoint check = new RecordingChecks.InsertionPoint("ip", List.of());
        InsertionPointCheckAdapter adapter = adapterFor(check, true);

        assertThat(adapter.descriptor()).isSameAs(check.descriptor());
        assertThat(adapter.checkName()).isEqualTo(check.descriptor().issueName());
    }

    @Test
    void delegatesConsolidationToTheWrappedCheck() {
        InsertionPointCheckAdapter adapter = adapterFor(new RecordingChecks.InsertionPoint("ip", List.of()), true);
        AuditIssue existing = mock(AuditIssue.class);
        AuditIssue duplicate = mock(AuditIssue.class);
        when(existing.name()).thenReturn("Same");
        when(duplicate.name()).thenReturn("Same");

        assertThat(adapter.consolidateIssues(existing, duplicate)).isEqualTo(ConsolidationAction.KEEP_EXISTING);
    }

    @Test
    void rejectsACheckWhoseDescriptorDeclaresAnotherKind() {
        RecordingChecks.InsertionPoint mislabelled = new RecordingChecks.InsertionPoint(
                RecordingChecks.descriptorFor("ip", CheckKind.HOST), List.of());

        assertThatThrownBy(() -> adapterFor(mislabelled, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ip");
    }

    private static InsertionPointCheckAdapter adapterFor(RecordingChecks.InsertionPoint check, boolean enabled) {
        return new InsertionPointCheckAdapter(AdapterFixtures.settingsAllowing(enabled), McpEventLog.noop(), check);
    }
}
