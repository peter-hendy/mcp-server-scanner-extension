package com.mcpscanner.proxy.observe.preflight;

/**
 * Outcome of a single {@link PreflightCheck}.
 *
 * <p>{@link #WARN} means "the operator must verify this manually; Montoya exposes no API to check it
 * programmatically". It is deliberately distinct from {@link #FAIL} so the proxy never claims to have
 * verified something it cannot — matching the scanner's anti-false-positive ethos.
 */
public enum PreflightStatus {
    OK,
    WARN,
    FAIL
}
