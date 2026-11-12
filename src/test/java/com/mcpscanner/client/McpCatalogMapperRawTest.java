package com.mcpscanner.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpCatalogMapperRawTest {

    private static final String MINIMAL_2024_TOOL =
            "{\"name\":\"echo\",\"description\":\"Echo\",\"inputSchema\":{\"type\":\"object\"}}";
    private static final String FULL_2025_TOOL = "{\"name\":\"search\",\"title\":\"Search\","
            + "\"description\":\"Find\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}},"
            + "\"outputSchema\":{\"type\":\"object\"},\"annotations\":{\"title\":\"S\",\"readOnlyHint\":true},"
            + "\"icons\":[{\"src\":\"https://x/i.png\",\"mimeType\":\"image/png\",\"sizes\":[\"48x48\"]}],"
            + "\"execution\":{\"taskSupport\":\"optional\"},\"_meta\":{\"k\":\"v\"},\"x-vendor\":1}";
    private static final String RESOURCE = "{\"uri\":\"file:///a\",\"name\":\"a\",\"title\":\"A\","
            + "\"mimeType\":\"text/plain\",\"size\":12,\"annotations\":{\"audience\":[\"user\"]},\"x-vendor\":1}";
    private static final String TEMPLATE = "{\"uriTemplate\":\"file:///{p}\",\"name\":\"t\","
            + "\"_meta\":{\"k\":1},\"x-vendor\":1}";
    private static final String PROMPT = "{\"name\":\"p\",\"title\":\"P\","
            + "\"arguments\":[{\"name\":\"a\",\"required\":true}],\"x-vendor\":1}";
    private static final String LEGACY_RESULT = "{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{}},"
            + "\"serverInfo\":{\"name\":\"s\",\"version\":\"1\"},\"instructions\":\"hi\"}";
    private static final String MODERN_RESULT = "{\"capabilities\":{\"tools\":{}},"
            + "\"_meta\":{\"io.modelcontextprotocol/serverInfo\":{\"name\":\"s\",\"version\":\"2\"}},\"x-vendor\":1}";

    private final McpCatalogMapper mapper = new McpCatalogMapper();

    @Test
    void minimalToolRawIsTheReceivedEntry() {
        assertThat(serialized(mapper.tools(listOf("tools", MINIMAL_2024_TOOL)).get(0).raw()))
                .isEqualTo(MINIMAL_2024_TOOL);
    }

    @Test
    void fullToolRawKeepsEveryFieldInReceivedOrder() {
        assertThat(serialized(mapper.tools(listOf("tools", FULL_2025_TOOL)).get(0).raw()))
                .isEqualTo(FULL_2025_TOOL);
    }

    @Test
    void resourceRawIsTheReceivedEntry() {
        assertThat(serialized(mapper.resources(listOf("resources", RESOURCE)).get(0).raw())).isEqualTo(RESOURCE);
    }

    @Test
    void templateRawIsTheReceivedEntry() {
        assertThat(serialized(mapper.templates(listOf("resourceTemplates", TEMPLATE)).get(0).raw()))
                .isEqualTo(TEMPLATE);
    }

    @Test
    void promptRawIsTheReceivedEntry() {
        assertThat(serialized(mapper.prompts(listOf("prompts", PROMPT)).get(0).raw())).isEqualTo(PROMPT);
    }

    @Test
    void legacyServerMetadataRawIsTheWholeResult() {
        assertThat(serialized(mapper.serverMetadata(envelope(LEGACY_RESULT)).raw())).isEqualTo(LEGACY_RESULT);
    }

    @Test
    void modernServerMetadataRawIsTheWholeResult() {
        assertThat(serialized(mapper.serverMetadata(envelope(MODERN_RESULT)).raw())).isEqualTo(MODERN_RESULT);
    }

    @Test
    void typedFieldsAreStillMapped() {
        assertThat(mapper.tools(listOf("tools", FULL_2025_TOOL)).get(0).annotations().isExplicitlyReadOnly())
                .isTrue();
        assertThat(mapper.serverMetadata(envelope(MODERN_RESULT)).serverInfo()).containsEntry("version", "2");
    }

    private static Map<String, Object> listOf(String key, String entry) {
        return parse("{\"" + key + "\":[" + entry + "]}");
    }

    private static Map<String, Object> envelope(String result) {
        return parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + result + "}");
    }

    private static Map<String, Object> parse(String json) {
        try {
            return McpObjectMapper.INSTANCE.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String serialized(JsonNode node) {
        return node.toString();
    }
}
