package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class McpServerConfigTest {

    @Test
    void rejectsMissingAuthStrategyByName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new McpServerConfig("https://mcp.example/mcp", TransportType.AUTO, null))
                .withMessage("auth");
    }

    @Test
    void acceptsNoAuthStrategyAsTheUnauthenticatedChoice() {
        NoAuthStrategy none = new NoAuthStrategy();

        McpServerConfig config = new McpServerConfig("https://mcp.example/mcp", TransportType.AUTO, none);

        assertThat(config.auth()).isSameAs(none);
    }
}
