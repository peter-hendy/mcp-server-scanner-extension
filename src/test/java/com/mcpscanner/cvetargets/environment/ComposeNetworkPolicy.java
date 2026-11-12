package com.mcpscanner.cvetargets.environment;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ComposeNetworkPolicy {


    public List<String> violations(String resolvedComposeJson) {
        JsonNode config = ComposeConfigJson.parse(resolvedComposeJson);
        List<String> violations = new ArrayList<>();
        JsonNode networks = config.path("networks");
        if (networks.isEmpty()) {
            violations.add("compose must attach its services to the external network " + SharedTargetNetwork.NAME);
        }
        networks.properties().forEach(network -> checkNetwork(network, violations));
        config.path("services").properties().forEach(service -> checkService(service, violations));
        return violations;
    }

    private static void checkNetwork(Map.Entry<String, JsonNode> network, List<String> violations) {
        JsonNode definition = network.getValue();
        if (!definition.path("external").asBoolean(false)
                || !SharedTargetNetwork.NAME.equals(definition.path("name").asText())) {
            violations.add("network " + network.getKey() + " must be external and named " + SharedTargetNetwork.NAME);
        }
    }

    private static void checkService(Map.Entry<String, JsonNode> service, List<String> violations) {
        if (service.getValue().has("network_mode")) {
            violations.add("service " + service.getKey() + " must use the shared network, not network_mode");
        }
    }
}
