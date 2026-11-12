package com.mcpscanner.cvetargets.environment;

import com.mcpscanner.cvetargets.run.PeakUsage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class HostUsageReadings {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final Path HERE = Path.of(".");

    private final CommandRunner runner;

    public HostUsageReadings(CommandRunner runner) {
        this.runner = runner;
    }

    PeakUsage read(String composeProject) {
        return new PeakUsage(rssMb(List.of(ProcessHandle.current().pid())), rssMb(childJvms()),
                containersMb(composeProject));
    }

    private static List<Long> childJvms() {
        return ProcessHandle.current().descendants()
                .filter(child -> child.info().command().map(command -> command.endsWith("/java")).orElse(false))
                .map(ProcessHandle::pid)
                .toList();
    }

    private long rssMb(List<Long> pids) {
        if (pids.isEmpty()) {
            return 0;
        }
        String joined = pids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return ResidentSizes.psRssTotalMb(output(List.of("ps", "-o", "rss=", "-p", joined)));
    }

    private long containersMb(String composeProject) {
        List<String> ids = output(List.of("docker", "ps", "--quiet", "--filter",
                "label=com.docker.compose.project=" + composeProject)).lines()
                .map(String::strip).filter(id -> !id.isEmpty()).toList();
        if (ids.isEmpty()) {
            return 0;
        }
        List<String> stats = new ArrayList<>(List.of("docker", "stats", "--no-stream", "--format", "{{.MemUsage}}"));
        stats.addAll(ids);
        return ResidentSizes.dockerUsageTotalMb(output(stats));
    }

    private String output(List<String> command) {
        CommandResult result = runner.run(command, HERE, COMMAND_TIMEOUT);
        return result.succeeded() ? result.output() : "";
    }
}
