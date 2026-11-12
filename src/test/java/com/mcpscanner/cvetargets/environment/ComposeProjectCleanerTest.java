package com.mcpscanner.cvetargets.environment;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.mcpscanner.cvetargets.environment.ScriptedCommandRunner.result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComposeProjectCleanerTest {

    private static final Duration INTERVAL = Duration.ofMillis(250);

    private final List<Duration> pauses = new ArrayList<>();
    private final ComposeProjectCleaner cleaner = new ComposeProjectCleaner(pauses::add, 4, INTERVAL);

    private static DockerCompose compose(ScriptedCommandRunner runner) {
        return new DockerCompose(runner, new ComposeProject("cvet-target-arm", Path.of("/targets/t/compose.yaml"), 50000,
                Path.of("/targets/.cache/downloads")));
    }

    @Test
    void finishesAfterOneDownWhenNoContainerRemains() {
        ScriptedCommandRunner runner = new ScriptedCommandRunner();

        cleaner.clean(compose(runner));

        assertThat(runner.subcommands()).filteredOn(command -> command.contains(" down ")).hasSize(1);
        assertThat(pauses).isEmpty();
    }

    @Test
    void waitsForContainersStillBeingRemovedAndRepeatsDown() {
        ScriptedCommandRunner runner = new ScriptedCommandRunner()
                .onEachCall("ps --all --quiet", result(0, "helper1\n"), result(0, "helper1\n"), result(0, ""));

        cleaner.clean(compose(runner));

        assertThat(runner.subcommands()).filteredOn(command -> command.contains(" down ")).hasSize(3);
        assertThat(pauses).containsExactly(INTERVAL, INTERVAL);
    }

    @Test
    void retriesADownThatDockerRejectsWhileARemovalIsInProgress() {
        ScriptedCommandRunner runner = new ScriptedCommandRunner()
                .onEachCall(" down ", result(1, "container is marked for removal and cannot be started"),
                        result(0, ""));

        cleaner.clean(compose(runner));

        assertThat(runner.subcommands()).filteredOn(command -> command.contains(" down ")).hasSize(2);
        assertThat(pauses).containsExactly(INTERVAL);
    }

    @Test
    void reportsTheContainersStillPresentAfterTheLastAttempt() {
        ScriptedCommandRunner runner = new ScriptedCommandRunner().on("ps --all --quiet", 0, "abc123\n");

        assertThatThrownBy(() -> cleaner.clean(compose(runner)))
                .isInstanceOf(EnvironmentException.class)
                .hasMessageContaining("containers left after teardown: [abc123]");
        assertThat(runner.subcommands()).filteredOn(command -> command.contains(" down ")).hasSize(4);
        assertThat(pauses).hasSize(3);
    }

    @Test
    void reportsTheLastDownFailureWhenDockerNeverAcceptsIt() {
        ScriptedCommandRunner runner = new ScriptedCommandRunner().on(" down ", 1, "daemon unavailable");

        assertThatThrownBy(() -> cleaner.clean(compose(runner)))
                .isInstanceOf(EnvironmentException.class)
                .hasMessageContaining("daemon unavailable");
    }
}
