package com.mcpscanner.engine;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class IssueTally {

    static final int KEPT = 1_000;

    private record TestedIssue(AuditIssue issue, Optional<String> testedRequest) {
    }

    private final Bounded<TestedIssue> issues = new Bounded<>(KEPT);
    private final Map<AuditIssueSeverity, Integer> bySeverity = new EnumMap<>(AuditIssueSeverity.class);

    synchronized void add(List<AuditIssue> added, Optional<String> testedRequest) {
        added.forEach(issue -> add(new TestedIssue(issue, testedRequest)));
    }

    synchronized int total() {
        return issues.kept().total();
    }

    synchronized ScanFindings snapshot() {
        Bounded.Kept<TestedIssue> kept = issues.kept();
        return new ScanFindings(kept.latest().stream().map(TestedIssue::issue).toList(), kept.total(),
                Map.copyOf(bySeverity), testedRequests(kept.latest()));
    }

    private void add(TestedIssue tested) {
        issues.add(tested);
        if (isRated(tested.issue())) {
            bySeverity.merge(tested.issue().severity(), 1, Integer::sum);
        }
    }

    private static Map<AuditIssue, String> testedRequests(List<TestedIssue> kept) {
        Map<AuditIssue, String> named = new IdentityHashMap<>();
        kept.forEach(tested -> tested.testedRequest().ifPresent(name -> named.put(tested.issue(), name)));
        return Collections.unmodifiableMap(named);
    }

    private static boolean isRated(AuditIssue issue) {
        return issue.severity() != null;
    }
}
