package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.HttpService;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.ConnectionScoped;
import com.mcpscanner.checks.api.HostTrial;
import com.mcpscanner.checks.api.RetryingHostCheck;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.checks.shared.session.StreamingFallback;
import com.mcpscanner.checks.shared.stream.EventStreams;
import com.mcpscanner.logging.McpEventLog;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class OriginHostAdmissionCheck implements RetryingHostCheck, ConnectionScoped {

    private final List<HostTrial> trials;
    private final StreamingFallback streamedReplies = StreamingFallback.outsideBurp();

    public OriginHostAdmissionCheck(McpEventLog eventLog, CredentialHeaders credentials,
                                    Supplier<Optional<HttpService>> streamProxy, EventStreams streams,
                                    ProbeSessions probeSessions) {
        CheckLog log = new CheckLog(eventLog, OriginHostAdmissionDescriptor.DESCRIPTOR.id());
        AdmissionDecisions decisions = new AdmissionDecisions(log, credentials);
        this.trials = List.of(
                new RequestAdmissionHostTrial(decisions, credentials, streamedReplies, streamProxy,
                        new FreshProbeSession(probeSessions, log)),
                new StreamAdmissionHostTrial(log, decisions, credentials, streamProxy, streams));
    }

    @Override
    public CheckDescriptor descriptor() {
        return OriginHostAdmissionDescriptor.DESCRIPTOR;
    }

    @Override
    public List<HostTrial> trials() {
        return trials;
    }

    @Override
    public void clearConnectionState() {
        streamedReplies.forgetStreamedRoutes();
    }
}
