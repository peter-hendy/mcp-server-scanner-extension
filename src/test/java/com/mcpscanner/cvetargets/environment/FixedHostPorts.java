package com.mcpscanner.cvetargets.environment;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public final class FixedHostPorts {

    private static final String ALL_INTERFACES = "0.0.0.0";

    private FixedHostPorts() {
    }

    public static List<HostPort> of(String resolvedComposeJson, String armService, int harnessChosenPort) {
        JsonNode services = ComposeConfigJson.parse(resolvedComposeJson).path("services");
        List<HostPort> fixed = new ArrayList<>();
        for (String service : ArmServices.startedWith(services, armService)) {
            for (JsonNode binding : services.path(service).path("ports")) {
                String published = binding.path("published").asText("");
                if (!published.isEmpty() && portOf(service, published) != harnessChosenPort) {
                    fixed.add(new HostPort(service, binding.path("host_ip").asText(ALL_INTERFACES), portOf(service, published)));
                }
            }
        }
        return fixed;
    }

    private static int portOf(String service, String published) {
        try {
            return Integer.parseInt(published);
        } catch (NumberFormatException range) {
            throw new EnvironmentException("service " + service + " publishes host port " + published
                    + "; publish one fixed port or an ephemeral one");
        }
    }
}
