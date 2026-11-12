package com.mcpscanner.checks.registry;

import burp.api.montoya.scanner.AuditResult;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanCheckLoggingTest {

    private McpEventLog eventLog;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        eventLog = new McpEventLog(null);
    }

    @Test
    void emits_started_line_before_check_runs() {
        AuditResult empty = AuditResult.auditResult(List.of());

        ScanCheckLogging.runAndLog(eventLog, "demo-check", tally -> empty);

        List<String> infos = infos();
        assertThat(infos).hasSize(2);
        assertThat(infos.get(0)).isEqualTo("Scan check: demo-check — started");
        assertThat(infos.get(1))
                .startsWith("Scan check: demo-check — finished in ")
                .endsWith("ms, 0 requests, 0 issues");
    }

    @Test
    void finish_line_counts_the_requests_the_invocation_sent() {
        ScanCheckLogging.runAndLog(eventLog, "demo-check", tally -> {
            tally.record();
            tally.record();
            return AuditResult.auditResult(List.of());
        });

        assertThat(infos().get(1))
                .matches("Scan check: demo-check — finished in \\d+ms, 2 requests, 0 issues");
    }

    @Test
    void keeps_the_finish_line_of_an_invocation_that_reported_issues() {
        AuditResult oneIssue = AuditResult.auditResult(List.of(org.mockito.Mockito.mock(
                burp.api.montoya.scanner.audit.issues.AuditIssue.class)));

        ScanCheckLogging.runAndLog(eventLog, "demo-check", tally -> oneIssue);

        assertThat(eventLog.snapshot()).filteredOn(McpEventLog.LogEntry::kept)
                .extracting(McpEventLog.LogEntry::message).singleElement().asString().endsWith("ms, 0 requests, 1 issue");
    }

    @Test
    void does_not_keep_the_finish_line_of_an_invocation_without_issues() {
        ScanCheckLogging.runAndLog(eventLog, "demo-check", tally -> AuditResult.auditResult(List.of()));

        assertThat(eventLog.snapshot()).noneMatch(McpEventLog.LogEntry::kept);
    }

    private List<String> infos() {
        return eventLog.snapshot().stream()
                .filter(e -> e.level() == McpEventLog.Level.INFO)
                .map(McpEventLog.LogEntry::message)
                .toList();
    }
}
