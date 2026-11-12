package com.mcpscanner.scan.request;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerCapabilitiesTest {

    @Test
    void nullCapabilityValuesAreTreatedAsNotAdvertised() {
        Map<String, Object> advertised = new HashMap<>();
        advertised.put("logging", null);
        advertised.put("tools", Map.of());

        ServerCapabilities capabilities = new ServerCapabilities(advertised);

        assertThat(capabilities.advertises("tools")).isTrue();
        assertThat(capabilities.advertises("logging")).isFalse();
    }

    @Test
    void subscriptionNeedsAnExplicitTrueFlag() {
        assertThat(new ServerCapabilities(Map.of("resources", Map.of("subscribe", true)))
                .advertisesResourceSubscription()).isTrue();
        assertThat(new ServerCapabilities(Map.of("resources", Map.of("subscribe", "yes")))
                .advertisesResourceSubscription()).isFalse();
        assertThat(new ServerCapabilities(Map.of("resources", Map.of())).advertisesResourceSubscription()).isFalse();
        assertThat(ServerCapabilities.none().advertisesResourceSubscription()).isFalse();
    }

    @Test
    void nullMapMeansNothingAdvertised() {
        assertThat(new ServerCapabilities(null).advertised()).isEmpty();
    }
}
