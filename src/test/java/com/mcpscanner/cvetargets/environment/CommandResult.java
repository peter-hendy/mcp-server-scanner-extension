package com.mcpscanner.cvetargets.environment;

import java.util.List;

public record CommandResult(List<String> command, int exitCode, String output) {

    public boolean succeeded() {
        return exitCode == 0;
    }

    public CommandResult requireSuccess() {
        if (!succeeded()) {
            throw new EnvironmentException(String.join(" ", command) + " exited " + exitCode + ": " + output.strip());
        }
        return this;
    }
}
