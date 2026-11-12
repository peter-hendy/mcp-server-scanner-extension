package com.mcpscanner.cvetargets.environment;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class SharedNetworkNames {

    private static final Duration TIMEOUT = Duration.ofMinutes(1);
    private static final String FORMAT =
            "{{.Names}}\t{{.Label \"com.docker.compose.project\"}}\t{{.Label \"com.docker.compose.service\"}}";

    private final CommandRunner runner;
    private final Path workingDirectory;

    SharedNetworkNames(CommandRunner runner, Path workingDirectory) {
        this.runner = runner;
        this.workingDirectory = workingDirectory;
    }

    List<Holder> heldElsewhere(Set<String> serviceNames, String ownProject) {
        return runner.run(List.of("docker", "ps", "--filter", "network=" + SharedTargetNetwork.NAME, "--format", FORMAT),
                        workingDirectory, TIMEOUT)
                .requireSuccess().output().lines()
                .map(Holder::parse)
                .flatMap(Optional::stream)
                .filter(holder -> serviceNames.contains(holder.service()) && !ownProject.equals(holder.project()))
                .toList();
    }

    record Holder(String container, String project, String service) {

        private static Optional<Holder> parse(String line) {
            String[] fields = line.strip().split("\t", -1);
            return fields.length == 3 ? Optional.of(new Holder(fields[0], fields[1], fields[2])) : Optional.empty();
        }

        String describe() {
            return "service name " + service + " is already on the shared network " + SharedTargetNetwork.NAME
                    + ", held by container " + container + " (project " + project + ")";
        }
    }
}
