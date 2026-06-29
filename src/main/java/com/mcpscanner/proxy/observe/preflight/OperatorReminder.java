package com.mcpscanner.proxy.observe.preflight;

/**
 * Base for the honest {@link PreflightStatus#WARN} items: settings Montoya exposes no API to read, so
 * the proxy cannot truly verify them. Each reminder carries a fixed label and an actionable message
 * naming the exact setting or action the operator must check by hand.
 */
abstract class OperatorReminder implements PreflightCheck {

    private final String label;
    private final String action;

    OperatorReminder(String label, String action) {
        this.label = label;
        this.action = action;
    }

    @Override
    public final String label() {
        return label;
    }

    @Override
    public final PreflightResult run() {
        return PreflightResult.warn(action);
    }
}
