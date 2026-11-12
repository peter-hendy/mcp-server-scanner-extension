package com.mcpscanner.cvetargets.identities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpscanner.cvetargets.environment.CommandResult;
import com.mcpscanner.cvetargets.environment.CommandRunner;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class ArmSetupScript {

    static final Path SCRIPT = Path.of("_shared", "crossidentity", "arm_setup.py");
    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final CommandRunner commands;

    public ArmSetupScript(CommandRunner commands) {
        this.commands = commands;
    }

    public ArmSetupReport run(LoadedTarget target, String armName, URI endpoint, Map<String, String> primaryHeaders,
                              Map<String, ProvisionedIdentity> provisioned) {
        Path targetsRoot = target.directory().getParent().getParent();
        CommandResult result = commands.run(List.of(targetsRoot.resolve(SCRIPT).toString(),
                target.directory().toString(), armName, endpoint.toString(), json(primaryHeaders), json(provisioned)),
                target.directory(), TIMEOUT);
        if (!result.succeeded()) {
            throw new IllegalStateException("identity setup failed (exit " + result.exitCode() + "): "
                    + OutputLines.tail(result.output()));
        }
        return parse(result.output());
    }

    private static ArmSetupReport parse(String output) {
        try {
            return JSON.readValue(OutputLines.last(output), ArmSetupReport.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("identity setup printed no report: " + OutputLines.tail(output), e);
        }
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
