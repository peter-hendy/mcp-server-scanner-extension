package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.HostSettlement;
import com.mcpscanner.checks.api.HostTrial;
import com.mcpscanner.checks.api.HostTrialResult;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.api.SkipSubject;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.checks.shared.session.StreamingFallback;

import java.util.Optional;
import java.util.function.Supplier;

final class RequestAdmissionHostTrial implements HostTrial {

    private static final String FRESH_SESSION_LABEL = ", in a fresh session (the captured session was not usable)";

    private final AdmissionDecisions decisions;
    private final Supplier<Optional<HttpService>> streamProxy;
    private final AdmissionRoute direct;
    private final AdmissionRoute ownSseStream;
    private final FreshProbeSession freshSession;
    private final ReplaySafeRequests replaySafe = new ReplaySafeRequests();

    RequestAdmissionHostTrial(AdmissionDecisions decisions, CredentialHeaders credentials,
                              StreamingFallback streamedReplies, Supplier<Optional<HttpService>> streamProxy,
                              FreshProbeSession freshSession) {
        this.decisions = decisions;
        this.streamProxy = streamProxy;
        this.direct = AdmissionRoute.direct(credentials, streamedReplies);
        this.ownSseStream = AdmissionRoute.ownSseStream(credentials, streamedReplies);
        this.freshSession = freshSession;
    }

    @Override
    public Optional<SkipReason> whyNotApplicable(HttpRequestResponse hostBaseRequestResponse) {
        return replaySafe.whyRefused(hostBaseRequestResponse.request());
    }

    @Override
    public String subject(HttpRequestResponse hostBaseRequestResponse) {
        return "the Origin/Host request test on " + SkipSubject.of(hostBaseRequestResponse);
    }

    @Override
    public HostSettlement settlementOnFailure() {
        return HostSettlement.SETTLED;
    }

    @Override
    public HostTrialResult run(HttpRequestResponse hostBaseRequestResponse, Http http) {
        String test = subject(hostBaseRequestResponse);
        AdmissionRoute route = route();
        DeliveredTrial trial = retriedInFreshSession(route,
                attempt(route, route.label(), route.deliver(hostBaseRequestResponse.request()), http),
                hostBaseRequestResponse, http);
        TrialOutcome outcome = trial.outcome();
        return new HostTrialResult(decisions.decide(test, outcome, UnprobedTrials.requestTrial(outcome),
                trial.label(), trial.delivered()), settlementOf(outcome));
    }

    private DeliveredTrial retriedInFreshSession(AdmissionRoute route, DeliveredTrial trial,
                                                 HttpRequestResponse hostBase, Http http) {
        if (route != direct || !trial.refusedInCapturedSession()) {
            return trial;
        }
        return freshSession.within(hostBase, trial.delivered(), http,
                        fresh -> attempt(route, route.label() + FRESH_SESSION_LABEL, fresh, http))
                .orElse(trial);
    }

    private static DeliveredTrial attempt(AdmissionRoute route, String label, HttpRequest delivered, Http http) {
        return new DeliveredTrial(label, delivered, route.run(delivered, http));
    }

    private AdmissionRoute route() {
        return streamProxy.get().isPresent() ? ownSseStream : direct;
    }

    private static HostSettlement settlementOf(TrialOutcome outcome) {
        if (outcome.baselineUnansweredOutsideBurp()) {
            return HostSettlement.SETTLED;
        }
        if (outcome.status() == TrialOutcome.TrialStatus.BASELINE_NOT_SUCCESSFUL) {
            return HostSettlement.NOT_PROBED;
        }
        return outcome.fullyAnswered() ? HostSettlement.SETTLED : HostSettlement.INCONCLUSIVE;
    }
}
