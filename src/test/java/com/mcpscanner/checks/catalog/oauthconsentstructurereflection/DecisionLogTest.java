package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import burp.api.montoya.http.Http;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DecisionLogTest {

    private final McpEventLog eventLog = mock(McpEventLog.class);
    private final DecisionLog decisions =
            new DecisionLog(new CheckLog(eventLog, "check-x"), new OAuthHttp(mock(Http.class)));

    @Test
    void skippedLogsTheReasonAndYieldsNothing() {
        assertThat(decisions.notRun("https://h/mcp", SkipReason.prerequisiteMissing("no server"))).isEmpty();

        verify(eventLog).info("Scan check: check-x — skipped https://h/mcp (prerequisite missing: no server)");
    }

    @Test
    void notReportedLogsTheDecisionAndYieldsNothing() {
        assertThat(decisions.notReported("same structure")).isEmpty();

        verify(eventLog).info("Scan check: check-x — decision: not reported — same structure");
    }
}
