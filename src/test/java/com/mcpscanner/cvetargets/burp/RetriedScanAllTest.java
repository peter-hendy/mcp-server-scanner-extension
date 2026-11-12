package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.AuditSnapshot;
import com.mcpscanner.automation.CheckAccessCommand;
import com.mcpscanner.automation.CheckAccessStatus;
import com.mcpscanner.automation.LaunchedScan;
import com.mcpscanner.automation.ScanAllCommand;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetriedScanAllTest {

    private static final ScanAllCommand COMMAND = new ScanAllCommand("http://127.0.0.1:1/mcp", null, null, null,
            null, null, false, null);

    @Test
    void retriesAConnectThatFailedLikeAUserClickingConnectAgain() {
        assertThat(RetriedScanAll.launch(failingTimes(2), COMMAND).requests()).isEqualTo(7);
    }

    @Test
    void givesUpAfterThreeAttemptsWithEveryReason() {
        assertThatThrownBy(() -> RetriedScanAll.launch(failingTimes(3), COMMAND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 times")
                .hasMessageContaining("attempt 1").hasMessageContaining("attempt 3");
    }

    private static BurpAutomation failingTimes(int failures) {
        AtomicInteger attempts = new AtomicInteger();
        return new BurpAutomation() {
            @Override
            public LaunchedScan scanAll(ScanAllCommand command) {
                int attempt = attempts.incrementAndGet();
                if (attempt <= failures) {
                    throw new IllegalStateException("attempt " + attempt + ": Failed to establish scanner session");
                }
                return new LaunchedScan(7);
            }

            @Override
            public Optional<AuditSnapshot> audit() {
                return Optional.empty();
            }

            @Override
            public CheckAccessStatus checkAccess(CheckAccessCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<CheckAccessStatus> checkAccessStatus() {
                return Optional.empty();
            }
        };
    }
}
