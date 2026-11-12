package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public class ScriptedTrial implements HostTrial {

    public static final SkipReason NOT_APPLICABLE = SkipReason.notApplicable("scripted to skip");

    private final List<AuditIssue> issues;
    private final HostSettlement settlement;
    private final Predicate<HttpRequestResponse> applicable;
    public final List<HttpRequestResponse> runs = new CopyOnWriteArrayList<>();

    public ScriptedTrial(HostSettlement settlement, List<AuditIssue> issues) {
        this(settlement, issues, base -> true);
    }

    public ScriptedTrial(HostSettlement settlement, List<AuditIssue> issues, Predicate<HttpRequestResponse> applicable) {
        this.settlement = settlement;
        this.issues = issues;
        this.applicable = applicable;
    }

    public static ScriptedTrial settling(HostSettlement settlement) {
        return new ScriptedTrial(settlement, List.of());
    }

    @Override
    public Optional<SkipReason> whyNotApplicable(HttpRequestResponse base) {
        return applicable.test(base) ? Optional.empty() : Optional.of(NOT_APPLICABLE);
    }

    @Override
    public HostTrialResult run(HttpRequestResponse base, Http http) {
        runs.add(base);
        return new HostTrialResult(issues, settlement);
    }

    public static final class Check implements RetryingHostCheck {

        private final List<HostTrial> trials;

        public Check(HostTrial... trials) {
            this.trials = List.of(trials);
        }

        @Override
        public CheckDescriptor descriptor() {
            return RecordingChecks.descriptorFor("retrying", CheckKind.HOST);
        }

        @Override
        public List<HostTrial> trials() {
            return trials;
        }
    }
}
