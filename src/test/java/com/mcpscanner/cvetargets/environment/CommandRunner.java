package com.mcpscanner.cvetargets.environment;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@FunctionalInterface
public interface CommandRunner {
    CommandResult run(List<String> command, Path workingDirectory, Duration timeout);
}
