package com.mcpscanner.cvetargets.environment;

import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.TargetArm;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class DockerTargetEnvironment implements TargetEnvironment {

    private static final String LOOPBACK = "127.0.0.1";

    private final CommandRunner runner;
    private final ComposeProjectCleaner cleaner;
    private final HostPortProbe hostPorts;
    private final DownloadSource downloads;
    private final ComposeNetworkPolicy networkPolicy = new ComposeNetworkPolicy();

    public DockerTargetEnvironment(CommandRunner runner) {
        this(runner, ComposeProjectCleaner.standard());
    }

    DockerTargetEnvironment(CommandRunner runner, ComposeProjectCleaner cleaner) {
        this(runner, cleaner, HostPortProbe.BINDING);
    }

    DockerTargetEnvironment(CommandRunner runner, ComposeProjectCleaner cleaner, HostPortProbe hostPorts) {
        this(runner, cleaner, hostPorts, new HttpDownloadSource());
    }

    DockerTargetEnvironment(CommandRunner runner, ComposeProjectCleaner cleaner, HostPortProbe hostPorts,
                            DownloadSource downloads) {
        this.runner = runner;
        this.cleaner = cleaner;
        this.hostPorts = hostPorts;
        this.downloads = downloads;
    }

    @Override
    public RunningArm start(LoadedTarget target, TargetArm arm) {
        ComposeProject project = ComposeProject.forArm(target, arm);
        new DownloadCache(project.downloadsDirectory(), downloads, System.err::println)
                .ensure(target.manifest().downloads());
        DockerCompose compose = new DockerCompose(runner, project);
        String config = compose.config();
        requireSharedNetwork(config);
        new SharedTargetNetwork(runner, target.directory()).ensureExists();
        cleaner.clean(compose);
        requireFreeFixedPorts(config, arm.service(), project.hostPort());
        requireUnheldServiceNames(config, arm.service(), project, target);
        try {
            compose.up(arm.service());
            int port = compose.publishedPort(arm.service(), arm.endpoint().port());
            return new ComposeArm(compose, cleaner,
                    URI.create("http://" + LOOPBACK + ":" + port + arm.endpoint().path()));
        } catch (RuntimeException startFailure) {
            tearDownAfter(compose, startFailure);
            throw startFailure;
        }
    }

    private void requireSharedNetwork(String config) {
        List<String> violations = networkPolicy.violations(config);
        if (!violations.isEmpty()) {
            throw new EnvironmentException(String.join("; ", violations));
        }
    }

    private void requireFreeFixedPorts(String config, String service, int harnessChosenPort) {
        List<HostPort> busy = FixedHostPorts.of(config, service, harnessChosenPort).stream()
                .filter(port -> hostPorts.inUse(port.hostIp(), port.port()))
                .toList();
        if (!busy.isEmpty()) {
            throw new EnvironmentException("fixed host port " + busy.stream().map(HostPort::toString)
                    .collect(Collectors.joining(", "))
                    + " is already in use, probably by the same target running from another worktree or by another"
                    + " process; free it before running this arm");
        }
    }

    private void requireUnheldServiceNames(String config, String service, ComposeProject project,
                                           LoadedTarget target) {
        Set<String> helpers = ArmServices.helpersOf(ComposeConfigJson.parse(config).path("services"), service);
        List<SharedNetworkNames.Holder> held = new SharedNetworkNames(runner, target.directory())
                .heldElsewhere(helpers, project.name());
        if (!held.isEmpty()) {
            throw new EnvironmentException(held.stream().map(SharedNetworkNames.Holder::describe)
                    .collect(Collectors.joining("; "))
                    + "; that name would resolve to both projects' containers, so stop that project before running"
                    + " this arm");
        }
    }

    private void tearDownAfter(DockerCompose compose, RuntimeException startFailure) {
        try {
            cleaner.clean(compose);
        } catch (RuntimeException teardownFailure) {
            startFailure.addSuppressed(teardownFailure);
        }
    }

    private record ComposeArm(DockerCompose compose, ComposeProjectCleaner cleaner, URI endpoint)
            implements RunningArm {

        @Override
        public void close() {
            cleaner.clean(compose);
        }
    }
}
