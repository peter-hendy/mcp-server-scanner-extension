package com.mcpscanner.cvetargets.run;

import java.util.List;

public record CheckFindings(List<IssueSummary> issues, ProbeTally probes, List<IssueSummary> ambiguous) {

    public static final String UNATTRIBUTED = "unattributed";
    static final CheckFindings NONE = new CheckFindings(List.of(), ProbeTally.NONE);

    public CheckFindings {
        issues = List.copyOf(issues);
        ambiguous = List.copyOf(ambiguous);
    }

    public CheckFindings(List<IssueSummary> issues, ProbeTally probes) {
        this(issues, probes, List.of());
    }
}
