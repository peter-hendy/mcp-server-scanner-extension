package com.mcpscanner.proxy.observe.preflight;

import java.util.List;

/**
 * Aggregated outcome of a {@link ProxyPreflight} run: every check's labelled result, plus the one
 * question a caller actually gates on — did any real check {@link PreflightStatus#FAIL}? WARN items
 * never block; they are reminders the operator must act on, not failures the proxy can assert.
 */
public record PreflightReport(List<LabelledResult> results) {

    public PreflightReport(List<LabelledResult> results) {
        this.results = List.copyOf(results);
    }

    public boolean hasFailures() {
        return results.stream().anyMatch(result -> result.status() == PreflightStatus.FAIL);
    }
}
