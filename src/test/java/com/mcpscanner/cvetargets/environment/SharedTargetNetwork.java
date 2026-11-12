package com.mcpscanner.cvetargets.environment;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class SharedTargetNetwork {

    public static final String NAME = "mcp-cve-targets";
    public static final String SUBNET = "10.250.77.0/24";
    private static final Duration TIMEOUT = Duration.ofMinutes(1);

    private final CommandRunner runner;
    private final Path workingDirectory;

    public SharedTargetNetwork(CommandRunner runner, Path workingDirectory) {
        this.runner = runner;
        this.workingDirectory = workingDirectory;
    }

    public void ensureExists() {
        if (exists()) {
            return;
        }
        CommandResult created = docker("network", "create", "--subnet", SUBNET, NAME);
        if (!created.succeeded() && !exists()) {
            created.requireSuccess();
        }
    }

    private boolean exists() {
        return docker("network", "inspect", NAME).succeeded();
    }

    private CommandResult docker(String... arguments) {
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(List.of(arguments));
        return runner.run(command, workingDirectory, TIMEOUT);
    }
}
