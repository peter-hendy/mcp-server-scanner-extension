package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.HostSettlement;
import com.mcpscanner.checks.api.HostTrial;
import com.mcpscanner.checks.api.HostTrialResult;
import com.mcpscanner.checks.api.SkipReason;

import java.util.Optional;
import java.util.function.Supplier;

final class LeakHostTrial implements HostTrial {

    private static final SkipReason LEGACY_SSE =
            SkipReason.notApplicable("the legacy SSE transport has no Streamable HTTP probe sessions");
    private static final SkipReason NOT_ECHOABLE =
            SkipReason.notApplicable("needs a tools/call with a string argument to echo");

    private final CheckLog log;
    private final Supplier<Optional<HttpService>> sseProbeService;
    private final LeakTrial trial;
    private final CrossClientResponseLeakIssues issues;

    LeakHostTrial(CheckLog log, Supplier<Optional<HttpService>> sseProbeService, LeakTrial trial,
                  CrossClientResponseLeakIssues issues) {
        this.log = log;
        this.sseProbeService = sseProbeService;
        this.trial = trial;
        this.issues = issues;
    }

    @Override
    public Optional<SkipReason> whyNotApplicable(HttpRequestResponse hostBaseRequestResponse) {
        if (sseProbeService.get().isPresent()) {
            return Optional.of(LEGACY_SSE);
        }
        return EchoableCall.of(hostBaseRequestResponse.request()).isPresent()
                ? Optional.empty() : Optional.of(NOT_ECHOABLE);
    }

    @Override
    public HostSettlement settlementOnFailure() {
        return HostSettlement.SETTLED;
    }

    @Override
    public HostTrialResult run(HttpRequestResponse hostBaseRequestResponse, Http http) {
        EchoableCall call = EchoableCall.of(hostBaseRequestResponse.request()).orElseThrow();
        TrialOutcome outcome = trial.run(hostBaseRequestResponse, call, http);
        outcome.verdict().stopReason().ifPresentOrElse(
                reason -> log.notRun(subject(hostBaseRequestResponse), reason),
                () -> log.info("decision: " + outcome.summary()));
        return new HostTrialResult(issues.issueFor(outcome, hostBaseRequestResponse.request().url()).stream().toList(),
                outcome.verdict().settlement());
    }
}
