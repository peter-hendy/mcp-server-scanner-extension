package com.mcpscanner.cvetargets.environment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComposeNetworkPolicyTest {

    private final ComposeNetworkPolicy policy = new ComposeNetworkPolicy();

    @Test
    void acceptsServicesOnTheSharedExternalNetwork() {
        assertThat(policy.violations("{\"services\":{\"a\":{}},"
                + "\"networks\":{\"default\":{\"name\":\"mcp-cve-targets\",\"external\":true}}}")).isEmpty();
    }

    @Test
    void rejectsAProjectDefaultNetwork() {
        assertThat(policy.violations("{\"services\":{\"a\":{}}}"))
                .containsExactly("compose must attach its services to the external network mcp-cve-targets");
    }

    @Test
    void rejectsAnyOtherNetworkAndNetworkMode() {
        assertThat(policy.violations("{\"services\":{\"a\":{\"network_mode\":\"bridge\"}},"
                + "\"networks\":{\"default\":{\"name\":\"mcp-cve-targets\",\"external\":true},\"db\":{\"name\":\"p_db\"}}}"))
                .containsExactly("network db must be external and named mcp-cve-targets",
                        "service a must use the shared network, not network_mode");
    }
}
