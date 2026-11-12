package com.mcpscanner.engine;

@FunctionalInterface
public interface SkippedInvocations {

    SkippedInvocations UNREPORTED = (invocations, cause) -> { };

    void skipped(int invocations, SkipCause cause);
}
