package com.mcpscanner.proxy.observe.preflight;

/**
 * The status of one preflight check plus a short, actionable message for the operator.
 */
public record PreflightResult(PreflightStatus status, String message) {

    public static PreflightResult ok(String message) {
        return new PreflightResult(PreflightStatus.OK, message);
    }

    public static PreflightResult warn(String message) {
        return new PreflightResult(PreflightStatus.WARN, message);
    }

    public static PreflightResult fail(String message) {
        return new PreflightResult(PreflightStatus.FAIL, message);
    }
}
