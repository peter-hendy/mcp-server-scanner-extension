package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.shared.host.Claims;
import com.mcpscanner.logging.McpEventLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HostAudits {

    public static final int MAX_INCONCLUSIVE_ATTEMPTS = Claims.MAX_PROBED_ATTEMPTS;
    public static final int MAX_UNSUITABLE_SEEDS = Claims.MAX_UNSUITABLE_SEEDS;

    private final List<HostTrial> trials;
    private final CheckLog log;
    private final Claims<TrialClaim> claims = new Claims<>();

    public HostAudits(HostCheck check) {
        this(check, new CheckLog(McpEventLog.noop(), check.descriptor().id()));
    }

    public HostAudits(HostCheck check, CheckLog log) {
        this.trials = List.copyOf(check.trials());
        this.log = log;
    }

    public Optional<SkipReason> whyNotApplicable(HttpRequestResponse hostBaseRequestResponse) {
        List<SkipReason> reasons = trials.stream()
                .map(trial -> trial.whyNotApplicable(hostBaseRequestResponse))
                .flatMap(Optional::stream)
                .toList();
        return reasons.size() < trials.size() ? Optional.empty() : reasons.stream().findFirst();
    }

    public List<AuditIssue> audit(HttpRequestResponse hostBaseRequestResponse, Http http) {
        Optional<SkipReason> noTrialApplies = whyNotApplicable(hostBaseRequestResponse);
        if (noTrialApplies.isPresent()) {
            log.notRun(SkipSubject.of(hostBaseRequestResponse), noTrialApplies.get());
            return List.of();
        }
        HostKey host = HostKey.of(hostBaseRequestResponse.httpService());
        List<AuditIssue> issues = new ArrayList<>();
        for (int index = 0; index < trials.size(); index++) {
            issues.addAll(runIfClaimed(trials.get(index), new TrialClaim(host, index), hostBaseRequestResponse, http));
        }
        return List.copyOf(issues);
    }

    public void clear() {
        claims.clear();
    }

    private List<AuditIssue> runIfClaimed(HostTrial trial, TrialClaim claim,
                                          HttpRequestResponse hostBaseRequestResponse, Http http) {
        Optional<SkipReason> skip = trial.whyNotApplicable(hostBaseRequestResponse).or(() -> refusal(claim));
        if (skip.isPresent()) {
            log.notRun(trial.subject(hostBaseRequestResponse), skip.get());
            return List.of();
        }
        HostTrialResult result = runReleasingOnFailure(trial, claim, hostBaseRequestResponse, http);
        settle(claim, result.settlement());
        return result.issues();
    }

    private Optional<SkipReason> refusal(TrialClaim claim) {
        return claims.tryClaim(claim) ? Optional.empty() : Optional.of(HostRefusals.reasonFor(claims.whyRefused(claim)));
    }

    private HostTrialResult runReleasingOnFailure(HostTrial trial, TrialClaim claim,
                                                  HttpRequestResponse hostBaseRequestResponse, Http http) {
        try {
            return trial.run(hostBaseRequestResponse, http);
        } catch (RuntimeException failure) {
            settle(claim, trial.settlementOnFailure());
            throw failure;
        }
    }

    private void settle(TrialClaim claim, HostSettlement settlement) {
        switch (settlement) {
            case SETTLED -> {
            }
            case NOT_PROBED -> claims.releaseUnprobed(claim);
            case INCONCLUSIVE -> claims.releaseProbed(claim);
            case UNSUITABLE_SEED -> claims.releaseUnsuitableSeed(claim);
        }
    }

    private record TrialClaim(HostKey host, int trialIndex) {
    }
}
