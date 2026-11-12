package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.TestAutomationSwitch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

record HeadlessBurpCommand(BurpSettings settings, Path workDir, int automationPort) {

    static final String PROJECT_FILE = "project.burp";
    static final String USER_CONFIG_FILE = "user-config.json";
    static final String PROJECT_CONFIG_FILE = "project-config.json";
    static final String LOG_FILE = "burp.log";

    List<String> arguments() {
        List<String> arguments = new ArrayList<>(List.of(settings.javaExecutable(),
                "-Xmx" + settings.heap(),
                "-Djava.awt.headless=true",
                "-D" + TestAutomationSwitch.PORT_PROPERTY + "=" + automationPort,
                "-jar", settings.burpJar().toString(),
                "--project-file=" + workDir.resolve(PROJECT_FILE),
                "--user-config-file=" + workDir.resolve(USER_CONFIG_FILE)));
        projectConfig().ifPresent(config -> arguments.add("--config-file=" + workDir.resolve(PROJECT_CONFIG_FILE)));
        arguments.add("--disable-auto-update");
        arguments.add("--i-accept-the-license-agreement");
        return List.copyOf(arguments);
    }

    Optional<String> projectConfig() {
        if (!settings.configuration().divertsBuiltInChecks()) {
            return Optional.empty();
        }
        return Optional.of("""
                {"project_options":{"resource_pools":{"default_resource_pool":{"auto_backoff":false,\
                "auto_backoff_after_429_status_returned":false,"auto_backoff_after_503_status_returned":false}}}}
                """);
    }

    String userConfig() {
        return """
                {"user_options":{"extender":{"extensions":[{"errors":"console","output":"console",\
                "extension_type":"java","extension_file":"%s","loaded":true,"name":"MCP Server Scanner"}]}}}
                """.formatted(settings.extensionJar().toString().replace("\\", "\\\\").replace("\"", "\\\""));
    }
}
