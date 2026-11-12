package com.mcpscanner.checks.api;

import com.mcpscanner.checks.shared.scancontext.CurrentScan;
import com.mcpscanner.checks.shared.scancontext.ScanContext;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CheckLogTest {

    private final McpEventLog eventLog = mock(McpEventLog.class);
    private final CheckLog log = new CheckLog(eventLog, "demo-check");

    @Test
    void prefixesEveryMessageWithTheCheckId() {
        assertThat(CheckLog.prefix("demo-check")).isEqualTo("Scan check: demo-check — ");
    }

    @Test
    void infoMessagesCarryThePrefix() {
        log.info("skipped http://host/mcp");

        verify(eventLog).info("Scan check: demo-check — skipped http://host/mcp");
    }

    @Test
    void warningsCarryThePrefix() {
        log.warn("could not restore the session");

        verify(eventLog).warn("Scan check: demo-check — could not restore the session");
    }

    @Test
    void aSkipNamesTheSubjectAndTheReason() {
        log.notRun("tools/call echo at http://host/mcp", SkipReason.prerequisiteMissing("no Mcp-Session-Id"));

        verify(eventLog).info("Scan check: demo-check — skipped tools/call echo at http://host/mcp "
                + "(prerequisite missing: no Mcp-Session-Id)");
    }

    @Test
    void anInterruptionIsAStop() {
        log.notRun("field q", SkipReason.rateLimited());

        verify(eventLog).info("Scan check: demo-check — stopped field q (rate limited: the server answered HTTP 429)");
    }

    @Test
    void aReasonRepeatedInOneScanIsLoggedAtInfoOnceAndThenAtDebug() {
        ScanContext scan = new ScanContext(1L);

        CurrentScan.within(Optional.of(scan), () -> {
            log.notRun("field a", SkipReason.rateLimited());
            log.notRun("field b", SkipReason.rateLimited());
            return null;
        });

        verify(eventLog).info("Scan check: demo-check — stopped field a (rate limited: the server answered HTTP 429)");
        verify(eventLog).debug("Scan check: demo-check — stopped field b (rate limited: the server answered HTTP 429)");
    }

    @Test
    void theSameReasonIsLoggedAtInfoAgainInTheNextScan() {
        for (long scanId = 1; scanId <= 2; scanId++) {
            CurrentScan.within(Optional.of(new ScanContext(scanId)), () -> {
                log.notRun("field a", SkipReason.rateLimited());
                return null;
            });
        }

        verify(eventLog, times(2)).info(
                "Scan check: demo-check — stopped field a (rate limited: the server answered HTTP 429)");
    }

    @Test
    void aOncePerLoadReasonIsLoggedOnlyOnce() {
        log.notRunOncePerLoad("field a", SkipReason.prerequisiteMissing("no Collaborator"));
        log.notRunOncePerLoad("field b", SkipReason.prerequisiteMissing("no Collaborator"));

        verify(eventLog).info("Scan check: demo-check — skipped field a (prerequisite missing: no Collaborator)");
        verify(eventLog, never()).info("Scan check: demo-check — skipped field b (prerequisite missing: no Collaborator)");
    }
}
