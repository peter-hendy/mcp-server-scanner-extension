package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.HttpService;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.HostTrial;
import com.mcpscanner.checks.api.RetryingHostCheck;
import com.mcpscanner.checks.shared.random.RandomHex;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.logging.McpEventLog;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class CrossClientResponseLeakCheck implements RetryingHostCheck {

    private static final Duration RACE_REPLY_TIMEOUT = Duration.ofSeconds(15);
    private static final String MARKER_PREFIX = "mcpxc";
    private static final int MARKER_RANDOM_BYTES = 8;

    private final List<HostTrial> trials;

    public CrossClientResponseLeakCheck(McpEventLog eventLog, Supplier<AuthStrategy> authStrategy,
                                        Supplier<Optional<HttpService>> sseProbeService) {
        this(eventLog, authStrategy, sseProbeService, RACE_REPLY_TIMEOUT);
    }

    CrossClientResponseLeakCheck(McpEventLog eventLog, Supplier<AuthStrategy> authStrategy,
                                 Supplier<Optional<HttpService>> sseProbeService, Duration raceReplyTimeout) {
        CheckLog log = new CheckLog(eventLog, CrossClientResponseLeakDescriptor.DESCRIPTOR.id());
        Supplier<String> markers = () -> MARKER_PREFIX + RandomHex.of(MARKER_RANDOM_BYTES);
        LeakTrial leakTrial = new LeakTrial(new ProbeClients(new ProbeSessions(authStrategy, raceReplyTimeout), log),
                markers, new ConcurrentRound(markers, raceReplyTimeout));
        this.trials = List.of(new LeakHostTrial(log, sseProbeService, leakTrial,
                new CrossClientResponseLeakIssues(CrossClientResponseLeakDescriptor.DESCRIPTOR)));
    }

    @Override
    public CheckDescriptor descriptor() {
        return CrossClientResponseLeakDescriptor.DESCRIPTOR;
    }

    @Override
    public List<HostTrial> trials() {
        return trials;
    }
}
