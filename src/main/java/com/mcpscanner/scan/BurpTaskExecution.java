package com.mcpscanner.scan;

import burp.api.montoya.burpsuite.BurpSuite;
import burp.api.montoya.burpsuite.TaskExecutionEngine.TaskExecutionEngineState;

import java.util.Objects;

final class BurpTaskExecution {

    static final String PAUSED_WARNING = "Burp task execution is paused, so this Burp Scanner audit will not "
            + "run until it is resumed (Resume Burp tasks in the scan bar, or Burp's Dashboard)";

    private final BurpSuite burpSuite;

    BurpTaskExecution(BurpSuite burpSuite) {
        this.burpSuite = Objects.requireNonNull(burpSuite);
    }

    boolean paused() {
        return burpSuite.taskExecutionEngine().getState() == TaskExecutionEngineState.PAUSED;
    }

    void resume() {
        burpSuite.taskExecutionEngine().setState(TaskExecutionEngineState.RUNNING);
    }
}
