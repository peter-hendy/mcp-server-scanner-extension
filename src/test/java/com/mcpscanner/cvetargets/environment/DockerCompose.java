package com.mcpscanner.cvetargets.environment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class DockerCompose {

    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration QUERY_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration TEARDOWN_TIMEOUT = Duration.ofMinutes(3);
    private static final String STOP_GRACE_SECONDS = "1";

    private final CommandRunner runner;
    private final ComposeProject project;
    private final Path environmentFile;

    public DockerCompose(CommandRunner runner, ComposeProject project) {
        this.runner = runner;
        this.project = project;
        this.environmentFile = writeEnvironmentFile(project);
    }

    private static Path writeEnvironmentFile(ComposeProject project) {
        try {
            Path file = Files.createTempFile(project.name(), ".env");
            file.toFile().deleteOnExit();
            return Files.writeString(file, project.environmentFileContent());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the compose environment file", e);
        }
    }

    public String config() {
        return compose(QUERY_TIMEOUT, "config", "--format", "json").requireSuccess().output();
    }

    public void up(String service) {
        compose(BUILD_TIMEOUT, "up", "--detach", "--build", service).requireSuccess();
    }

    public int publishedPort(String service, int containerPort) {
        String binding = compose(QUERY_TIMEOUT, "port", service, String.valueOf(containerPort))
                .requireSuccess().output().strip();
        return Integer.parseInt(binding.substring(binding.lastIndexOf(':') + 1));
    }

    public List<String> containers() {
        return compose(QUERY_TIMEOUT, "ps", "--all", "--quiet").requireSuccess().output().lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    public void down() {
        compose(TEARDOWN_TIMEOUT, "down", "--volumes", "--remove-orphans", "--timeout", STOP_GRACE_SECONDS)
                .requireSuccess();
    }

    private CommandResult compose(Duration timeout, String... arguments) {
        List<String> command = new ArrayList<>(List.of("docker", "compose",
                "--file", project.composeFile().toString(), "--project-name", project.name(),
                "--env-file", environmentFile.toString()));
        command.addAll(List.of(arguments));
        return runner.run(command, project.directory(), timeout);
    }
}
