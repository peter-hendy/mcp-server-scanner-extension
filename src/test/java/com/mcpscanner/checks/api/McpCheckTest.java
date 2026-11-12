package com.mcpscanner.checks.api;

import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpCheckTest {

    private static final CheckVisitor<String> KIND_NAMER = new CheckVisitor<>() {
        @Override
        public String visitInsertionPointCheck(InsertionPointCheck check) {
            return "insertion-point";
        }

        @Override
        public String visitRequestCheck(RequestCheck check) {
            return "request";
        }

        @Override
        public String visitHostCheck(HostCheck check) {
            return "host";
        }
    };

    @Test
    void eachKindInterfaceDispatchesToItsOwnVisitorMethod() {
        assertThat(new RecordingChecks.InsertionPoint("a", List.of()).accept(KIND_NAMER)).isEqualTo("insertion-point");
        assertThat(new RecordingChecks.Request("b", List.of()).accept(KIND_NAMER)).isEqualTo("request");
        assertThat(new RecordingChecks.Host("c", List.of()).accept(KIND_NAMER)).isEqualTo("host");
    }

    @Test
    void defaultConsolidationKeepsTheExistingIssueWhenNamesMatch() {
        McpCheck check = new RecordingChecks.Request("a", List.of());

        assertThat(check.consolidate(issueNamed("Same"), issueNamed("Same")))
                .isEqualTo(ConsolidationAction.KEEP_EXISTING);
    }

    @Test
    void defaultConsolidationKeepsBothIssuesWhenNamesDiffer() {
        McpCheck check = new RecordingChecks.Request("a", List.of());

        assertThat(check.consolidate(issueNamed("One"), issueNamed("Other")))
                .isEqualTo(ConsolidationAction.KEEP_BOTH);
    }

    private static AuditIssue issueNamed(String name) {
        AuditIssue issue = mock(AuditIssue.class);
        when(issue.name()).thenReturn(name);
        return issue;
    }
}
