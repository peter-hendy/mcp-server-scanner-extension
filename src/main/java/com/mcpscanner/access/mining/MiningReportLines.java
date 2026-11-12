package com.mcpscanner.access.mining;

import com.mcpscanner.identity.ValueScope;

import java.util.List;
import java.util.stream.Stream;

final class MiningReportLines {

    private MiningReportLines() {
    }

    static MiningReport of(MiningResult result, MiningPlan plan) {
        List<String> warnings = Stream.concat(result.warnings().stream(),
                        result.identities().stream().flatMap(mining -> mining.warnings().stream()))
                .map(warning -> line(warning, plan)).distinct().toList();
        return new MiningReport(result.stop(), warnings, count(result, plan, ValueScope.PRIVATE),
                count(result, plan, ValueScope.SHARED));
    }

    private static String line(MiningWarning warning, MiningPlan plan) {
        return warning.identityId() == null ? warning.detail()
                : plan.nameOf(warning.identityId()) + ": " + warning.detail();
    }

    private static int count(MiningResult result, MiningPlan plan, ValueScope scope) {
        return (int) result.identities().stream()
                .filter(mining -> plan.identityIdsByPrincipal().containsKey(mining.identityId()))
                .flatMap(mining -> mining.values().stream()).filter(value -> value.scope() == scope).count();
    }
}
