package com.mcpscanner.proxy.observe.preflight;

/**
 * A single startup check the operator must pass (or acknowledge) before the proxy feature can be
 * trusted to observe MCP traffic. Implementations are either real automated checks (verifiable
 * through Montoya) or honest {@link PreflightStatus#WARN} reminders for settings Montoya cannot read.
 */
public interface PreflightCheck {

    /** A short, stable identity for what is being checked. */
    String label();

    /** Evaluate the check and report its status with an actionable message. */
    PreflightResult run();
}
