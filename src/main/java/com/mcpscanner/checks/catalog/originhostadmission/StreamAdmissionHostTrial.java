package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.HostSettlement;
import com.mcpscanner.checks.api.HostTrial;
import com.mcpscanner.checks.api.HostTrialResult;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.api.SkipSubject;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.checks.shared.stream.EventStreams;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

final class StreamAdmissionHostTrial implements HostTrial {

    private static final String VIA_EVENT_STREAM = "the GET event stream";
    private static final String LEGACY_SSE_MESSAGE_URL = "the URL is a legacy SSE message endpoint";
    private static final SkipReason SSE_HAS_NO_STREAM =
            SkipReason.notApplicable("the legacy SSE transport has no GET event stream");

    private final CheckLog log;
    private final AdmissionDecisions decisions;
    private final Supplier<Optional<HttpService>> streamProxy;
    private final StreamAdmissionTrial streamTrial;
    private final ReplaySafeRequests replaySafe = new ReplaySafeRequests();

    StreamAdmissionHostTrial(CheckLog log, AdmissionDecisions decisions, CredentialHeaders credentials,
                             Supplier<Optional<HttpService>> streamProxy, EventStreams streams) {
        this.log = log;
        this.decisions = decisions;
        this.streamProxy = streamProxy;
        this.streamTrial = new StreamAdmissionTrial(streams, credentials);
    }

    @Override
    public Optional<SkipReason> whyNotApplicable(HttpRequestResponse hostBaseRequestResponse) {
        return replaySafe.whyRefused(hostBaseRequestResponse.request())
                .or(() -> streamProxy.get().map(sse -> SSE_HAS_NO_STREAM));
    }

    @Override
    public String subject(HttpRequestResponse hostBaseRequestResponse) {
        return "the GET event-stream test on " + SkipSubject.of(hostBaseRequestResponse);
    }

    @Override
    public HostSettlement settlementOnFailure() {
        return HostSettlement.SETTLED;
    }

    @Override
    public HostTrialResult run(HttpRequestResponse hostBaseRequestResponse, Http http) {
        String test = subject(hostBaseRequestResponse);
        Optional<TrialOutcome> outcome = streamTrial.run(hostBaseRequestResponse, http);
        if (outcome.isEmpty()) {
            log.notRun(test, SkipReason.notApplicable(LEGACY_SSE_MESSAGE_URL));
            return HostTrialResult.settled(List.of());
        }
        TrialOutcome trial = outcome.get();
        return new HostTrialResult(decisions.decide(test, trial, UnprobedTrials.streamTrial(trial),
                VIA_EVENT_STREAM, trial.baseline().request()), settlementOf(trial));
    }

    private static HostSettlement settlementOf(TrialOutcome outcome) {
        return noStream(outcome) || outcome.fullyAnswered() ? HostSettlement.SETTLED : HostSettlement.INCONCLUSIVE;
    }

    private static boolean noStream(TrialOutcome outcome) {
        return outcome.status() == TrialOutcome.TrialStatus.BASELINE_NOT_SUCCESSFUL
                && outcome.baseline().response() != null;
    }
}
