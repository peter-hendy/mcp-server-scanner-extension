package com.mcpscanner.proxy.observe.preflight;

/**
 * Pairs a check's label with the {@link PreflightResult} it produced, so the aggregate report stays a
 * flat, ordered list the UI can render without re-running anything.
 */
public record LabelledResult(String label, PreflightResult result) {

    public PreflightStatus status() {
        return result.status();
    }
}
