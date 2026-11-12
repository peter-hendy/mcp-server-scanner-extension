package com.mcpscanner.cvetargets.run;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public record ArmObservation(List<IssueSummary> issues, int baseRequestCount, int unansweredBaseRequestCount,
                             ProbeTally probes, Object auditDetails, Map<String, CheckFindings> findingsByCheck) {

    public static final ArmObservation NONE = new ArmObservation(List.of(), 0, 0, ProbeTally.NONE, null);

    public ArmObservation {
        issues = List.copyOf(issues);
        findingsByCheck = Map.copyOf(findingsByCheck);
    }

    public ArmObservation(List<IssueSummary> issues, int baseRequestCount, int unansweredBaseRequestCount,
                          ProbeTally probes, Object auditDetails) {
        this(issues, baseRequestCount, unansweredBaseRequestCount, probes, auditDetails, Map.of());
    }

    public ArmObservation of(String checkId) {
        if (findingsByCheck.isEmpty()) {
            return this;
        }
        CheckFindings own = findingsByCheck.getOrDefault(checkId, CheckFindings.NONE);
        return new ArmObservation(own.issues(), baseRequestCount, unansweredBaseRequestCount, own.probes(),
                auditDetails);
    }

    public List<IssueSummary> ambiguousFor(String checkId) {
        return findingsByCheck.getOrDefault(checkId, CheckFindings.NONE).ambiguous();
    }

    public Map<String, List<IssueSummary>> issuesOtherThan(String checkId) {
        return findingsByCheck.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(checkId) && !entry.getValue().issues().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().issues(),
                        (first, second) -> first, TreeMap::new));
    }
}
