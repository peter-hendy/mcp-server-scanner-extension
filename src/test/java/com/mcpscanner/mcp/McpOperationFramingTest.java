package com.mcpscanner.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpOperationFramingTest {

    private static final String ENVELOPE = "\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
            + "\"io.modelcontextprotocol/clientInfo\":{\"name\":\"scanner\",\"version\":\"1\"},"
            + "\"io.modelcontextprotocol/clientCapabilities\":{}";

    @Test
    void removesTheProtocolEnvelopeAndKeepsClientMeta() throws JsonProcessingException {
        ObjectNode params = params("{\"name\":\"note\",\"_meta\":{" + ENVELOPE + ",\"progressToken\":\"p1\"}}");

        assertThat(McpOperationFraming.withoutProtocolEnvelope(params).toString())
                .isEqualTo("{\"name\":\"note\",\"_meta\":{\"progressToken\":\"p1\"}}");
    }

    @Test
    void removesMetaThatHeldOnlyTheProtocolEnvelope() throws JsonProcessingException {
        ObjectNode params = params("{\"name\":\"note\",\"_meta\":{" + ENVELOPE + "}}");

        assertThat(McpOperationFraming.withoutProtocolEnvelope(params).toString()).isEqualTo("{\"name\":\"note\"}");
    }

    @Test
    void leavesParamsWithoutMetaUnchanged() throws JsonProcessingException {
        ObjectNode params = params("{\"name\":\"note\",\"arguments\":{\"a\":\"x\"}}");

        assertThat(McpOperationFraming.withoutProtocolEnvelope(params)).isEqualTo(params);
    }

    @Test
    void doesNotChangeTheGivenParams() throws JsonProcessingException {
        String original = "{\"name\":\"note\",\"_meta\":{" + ENVELOPE + ",\"progressToken\":\"p1\"}}";
        ObjectNode params = params(original);

        McpOperationFraming.withoutProtocolEnvelope(params);

        assertThat(params).isEqualTo(params(original));
    }

    private static ObjectNode params(String json) throws JsonProcessingException {
        return (ObjectNode) McpObjectMapper.INSTANCE.readTree(json);
    }
}
