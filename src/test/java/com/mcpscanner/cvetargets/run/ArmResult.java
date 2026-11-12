package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.identities.IdentityEvidence;
import com.mcpscanner.cvetargets.manifest.ArmRole;
import com.mcpscanner.cvetargets.manifest.ExpectedOutcome;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.Outcome;
import com.mcpscanner.cvetargets.manifest.TargetArm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record ArmResult(
        String check,
        String target,
        String arm,
        String mode,
        ArmRole role,
        String version,
        ExpectedOutcome expected,
        Outcome observed,
        boolean passed,
        String verdict,
        List<IssueSummary> issues,
        int baseRequestCount,
        int unansweredBaseRequestCount,
        ProbeTally probes,
        long durationMillis,
        String startedAt,
        String error,
        Object auditDetails,
        IdentityEvidence identities,
        List<String> checkLog,
        Map<String, Long> phases,
        PeakUsage peakUsage,
        Map<String, List<IssueSummary>> otherCheckIssues,
        String notRunnable) {

    public ArmResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        probes = probes == null ? ProbeTally.NONE : probes;
        checkLog = checkLog == null ? List.of() : List.copyOf(checkLog);
        phases = phases == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(phases));
        peakUsage = peakUsage == null ? PeakUsage.NONE : peakUsage;
        otherCheckIssues = otherCheckIssues == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(otherCheckIssues));
    }

    public ArmResult(String check, String target, String arm, String mode, ArmRole role, String version,
                     ExpectedOutcome expected, Outcome observed, boolean passed, String verdict,
                     List<IssueSummary> issues, int baseRequestCount, int unansweredBaseRequestCount,
                     ProbeTally probes, long durationMillis, String startedAt, String error, Object auditDetails,
                     IdentityEvidence identities, List<String> checkLog, Map<String, Long> phases, PeakUsage peakUsage) {
        this(check, target, arm, mode, role, version, expected, observed, passed, verdict, issues, baseRequestCount,
                unansweredBaseRequestCount, probes, durationMillis, startedAt, error, auditDetails, identities, checkLog,
                phases, peakUsage, Map.of(), null);
    }

    public static ArmResult notRunnable(String check, LoadedTarget target, TargetArm arm, String mode, String reason) {
        return new ArmResult(check, target.manifest().id(), arm.name(), mode, arm.role(), arm.version(),
                arm.expected(), null, false, "not runnable: " + reason, List.of(), 0, 0, ProbeTally.NONE, 0, null,
                null, null, null, null, null, null, Map.of(), reason);
    }

    public static ArmResult crashed(String check, LoadedTarget target, TargetArm arm, Throwable failure) {
        String error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        return new ArmResult(check, target.manifest().id(), arm.name(), null, arm.role(), arm.version(),
                arm.expected(), null, false, "run failed: " + error, List.of(), 0, 0, ProbeTally.NONE, 0, null,
                error, null, null, null, null, null, Map.of(), null);
    }

    public ArmResult measured(Map<String, Long> measuredPhases, PeakUsage measuredPeak) {
        return new ArmResult(check, target, arm, mode, role, version, expected, observed, passed, verdict, issues,
                baseRequestCount, unansweredBaseRequestCount, probes, durationMillis, startedAt, error, auditDetails,
                identities, checkLog, measuredPhases, measuredPeak, otherCheckIssues, notRunnable);
    }
}
