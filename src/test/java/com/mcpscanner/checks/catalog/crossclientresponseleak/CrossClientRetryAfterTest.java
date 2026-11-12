package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.HttpService;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.registry.HostCheckAdapter;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrossClientRetryAfterTest {

    private final McpEventLog eventLog = new McpEventLog(null);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    @Timeout(20)
    void aRaceRateLimitedWithAShortRetryAfterStillStopsRacingTheHost() {
        LeakServers.Server server = LeakServers.rateLimitingTheRaceForAMinute();
        HostCheckAdapter adapter = adapter();

        adapter.doCheck(LeakServers.echoableToolCall(), null, server.http);
        int afterFirst = server.requestsSeen();
        adapter.doCheck(LeakServers.echoableToolCall(), null, server.http);

        assertThat(afterFirst).isEqualTo(2 + LeakTrial.CLIENTS);
        assertThat(server.requestsSeen()).isEqualTo(afterFirst);
        assertThat(eventLog.snapshot()).extracting(McpEventLog.LogEntry::message)
                .contains("Scan check: cross-client-response-leak — HTTP 429 from target.test: "
                        + "not waiting on a concurrent probe")
                .anyMatch(line -> line.contains("(rate limited: the server answered HTTP 429)"));
    }

    private HostCheckAdapter adapter() {
        ScanCheckSettings settings = mock(ScanCheckSettings.class);
        when(settings.isEnabled(anyString(), anyBoolean())).thenReturn(true);
        Optional<HttpService> noSseProbes = Optional.empty();
        return new HostCheckAdapter(settings, eventLog, new CrossClientResponseLeakCheck(eventLog,
                NoAuthStrategy::new, () -> noSseProbes, Duration.ofMillis(300)));
    }
}
