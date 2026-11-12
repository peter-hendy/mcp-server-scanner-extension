package com.mcpscanner.checks.shared.auth.oracle;

import burp.api.montoya.http.message.HttpRequestResponse;

public final class BaselineEquivalenceOracle {

    private final ResultEquivalence equivalence;
    private final ExchangeClassifier classifier = new ExchangeClassifier();

    public BaselineEquivalenceOracle(ResultEquivalence equivalence) {
        this.equivalence = equivalence;
    }

    public static BaselineEquivalenceOracle defaultOracle() {
        return new BaselineEquivalenceOracle(new OperationAwareEquivalence());
    }

    public OracleVerdict judge(OracleEvidence evidence) {
        ExchangeOutcome baseline = classifier.classify(evidence.authenticatedBaseline());
        if (!baseline.successful()) {
            return OracleVerdict.BASELINE_NOT_SUCCESSFUL;
        }
        if (evidence.refusalControls().isEmpty()) {
            return OracleVerdict.NO_REFUSAL_CONTROLS;
        }
        if (!evidence.refusalControls().stream().allMatch(this::refused)) {
            return OracleVerdict.CONTROL_NOT_REFUSED;
        }
        return judgeCandidate(evidence.authenticatedBaseline(), baseline, evidence.candidate());
    }

    public OracleVerdict judgeUncontrolled(HttpRequestResponse baselineExchange, HttpRequestResponse candidate) {
        ExchangeOutcome baseline = classifier.classify(baselineExchange);
        return baseline.successful()
                ? judgeCandidate(baselineExchange, baseline, candidate)
                : OracleVerdict.BASELINE_NOT_SUCCESSFUL;
    }

    public boolean refused(HttpRequestResponse exchange) {
        return classifier.classify(exchange).refused();
    }

    public boolean refusedByAuthentication(HttpRequestResponse exchange) {
        return classifier.refusedByAuthentication(exchange);
    }

    public boolean successful(HttpRequestResponse exchange) {
        return classifier.classify(exchange).successful();
    }

    private OracleVerdict judgeCandidate(HttpRequestResponse baselineExchange, ExchangeOutcome baseline,
                                         HttpRequestResponse candidateExchange) {
        ExchangeOutcome candidate = classifier.classify(candidateExchange);
        if (!candidate.successful()) {
            return OracleVerdict.CANDIDATE_NOT_SUCCESSFUL;
        }
        return matchesBaseline(baselineExchange, baseline, candidate)
                ? OracleVerdict.HIT : OracleVerdict.CANDIDATE_NOT_EQUIVALENT;
    }

    private boolean matchesBaseline(HttpRequestResponse baselineExchange, ExchangeOutcome baseline,
                                    ExchangeOutcome candidate) {
        if (classifier.isNotification(baselineExchange)) {
            return baseline.status() == candidate.status();
        }
        return baseline.result().isPresent() && candidate.result().isPresent()
                && equivalence.equivalent(baseline.result().get(), candidate.result().get());
    }
}
