package com.mcpscanner.cvetargets.burp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpscanner.automation.TestAutomationSwitch;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HeadlessBurpCommandTest {

    private static final Path WORK = Path.of("/tmp/work/burp-1");
    private final BurpSettings settings = new BurpSettings(Path.of("/opt/burp/burpsuite.jar"), "/usr/bin/java",
            Path.of("/repo/build/libs/ext \"quoted\".jar"), Path.of("/tmp/work"), "3g",
            BurpAuditConfiguration.EXTENSIONS_ONLY, false, Duration.ofMinutes(3), Duration.ofSeconds(5),
            Duration.ofMinutes(3), Duration.ofHours(2), 1);
    private final HeadlessBurpCommand command = new HeadlessBurpCommand(settings, WORK, 19911);

    @Test
    void startsHeadlessBurpOnAFreshProjectWithTheAutomationSwitchOn() {
        assertThat(command.arguments()).startsWith("/usr/bin/java").contains(
                "-Xmx3g",
                "-Djava.awt.headless=true",
                "-D" + TestAutomationSwitch.PORT_PROPERTY + "=19911",
                "/opt/burp/burpsuite.jar",
                "--project-file=" + WORK.resolve("project.burp"),
                "--user-config-file=" + WORK.resolve("user-config.json"),
                "--disable-auto-update",
                "--i-accept-the-license-agreement");
    }

    @Test
    void loadsTheExtensionJarThroughTheUserConfiguration() throws Exception {
        JsonNode extension = new ObjectMapper().readTree(command.userConfig())
                .path("user_options").path("extender").path("extensions").get(0);

        assertThat(extension.path("extension_file").asText()).isEqualTo("/repo/build/libs/ext \"quoted\".jar");
        assertThat(extension.path("extension_type").asText()).isEqualTo("java");
        assertThat(extension.path("loaded").asBoolean()).isTrue();
    }

    @Test
    void turnsOffBurpsAutomaticThrottlingWhenOnlyExtensionChecksRun() throws Exception {
        assertThat(command.arguments()).contains("--config-file=" + WORK.resolve("project-config.json"));
        JsonNode pool = new ObjectMapper().readTree(command.projectConfig().orElseThrow())
                .path("project_options").path("resource_pools").path("default_resource_pool");

        assertThat(pool.path("auto_backoff").isBoolean()).isTrue();
        assertThat(pool.path("auto_backoff").asBoolean()).isFalse();
        assertThat(pool.path("auto_backoff_after_429_status_returned").asBoolean(true)).isFalse();
        assertThat(pool.path("auto_backoff_after_503_status_returned").asBoolean(true)).isFalse();
    }

    @Test
    void leavesBurpsThrottlingAloneWhenBuiltInChecksRunToo() {
        HeadlessBurpCommand withBuiltIns = new HeadlessBurpCommand(new BurpSettings(settings.burpJar(),
                settings.javaExecutable(), settings.extensionJar(), settings.workRoot(), settings.heap(),
                BurpAuditConfiguration.WITH_BUILTINS, false, settings.startupTimeout(), settings.pollInterval(),
                settings.quietPeriod(), settings.minimumArmTimeout(), 1), WORK, 19911);

        assertThat(withBuiltIns.projectConfig()).isEmpty();
        assertThat(withBuiltIns.arguments()).noneMatch(argument -> argument.startsWith("--config-file="));
    }
}
