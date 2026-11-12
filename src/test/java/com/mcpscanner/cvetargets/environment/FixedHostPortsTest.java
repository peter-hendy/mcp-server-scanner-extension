package com.mcpscanner.cvetargets.environment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedHostPortsTest {

    private static final String CONFIG = """
            {"services":{
              "vulnerable":{"ports":[{"host_ip":"127.0.0.1","target":8000,"published":"47321"}],
                            "depends_on":{"helper":{"condition":"service_started"}}},
              "helper":{"ports":[{"host_ip":"127.0.0.1","target":9000,"published":"47330"}]},
              "fixed":{"ports":[{"host_ip":"127.0.0.1","target":8000,"published":"47322"}]},
              "ephemeral":{"ports":[{"host_ip":"127.0.0.1","target":8000,"published":""},{"target":8001}]},
              "chosen":{"ports":[{"host_ip":"127.0.0.1","target":8000,"published":"50123"}]}
            }}
            """;

    @Test
    void listsTheFixedPortsOfTheArmServiceAndEveryServiceItStarts() {
        assertThat(FixedHostPorts.of(CONFIG, "vulnerable", 50123))
                .containsExactlyInAnyOrder(new HostPort("vulnerable", "127.0.0.1", 47321),
                        new HostPort("helper", "127.0.0.1", 47330));
    }

    @Test
    void ignoresEphemeralPortsAndTheHarnessChosenHostPort() {
        assertThat(FixedHostPorts.of(CONFIG, "ephemeral", 50123)).isEmpty();
        assertThat(FixedHostPorts.of(CONFIG, "chosen", 50123)).isEmpty();
    }

    @Test
    void refusesAPublishedPortRangeClearly() {
        String config = "{\"services\":{\"arm\":{\"ports\":[{\"target\":80,\"published\":\"8000-8010\"}]}}}";

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> FixedHostPorts.of(config, "arm", 1))
                .isInstanceOf(EnvironmentException.class)
                .hasMessageContaining("service arm publishes host port 8000-8010");
    }

    @Test
    void readsAnUnboundHostIpAsAllInterfaces() {
        String config = "{\"services\":{\"arm\":{\"ports\":[{\"target\":80,\"published\":\"8080\"}]}}}";

        assertThat(FixedHostPorts.of(config, "arm", 1)).containsExactly(new HostPort("arm", "0.0.0.0", 8080));
    }
}
