package com.mcpscanner.engine;

import com.mcpscanner.logging.McpEventLog;

import java.time.Clock;
import java.util.function.LongToIntFunction;

public record EngineEnvironment(McpEventLog eventLog, Clock clock, ScanInstrumentation instrumentation,
                                RequestNames requestNames, LongToIntFunction watchedPayloads) {
}
