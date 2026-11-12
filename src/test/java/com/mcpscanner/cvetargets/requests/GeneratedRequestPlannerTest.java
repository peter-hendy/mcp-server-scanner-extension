package com.mcpscanner.cvetargets.requests;

import com.mcpscanner.testutil.PlainFraming;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpOperationFraming;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.PromptArgument;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.scan.field.TemplateExpansionRegistry;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedRequestPlannerTest {

    private static final String ENDPOINT = "http://target.test:8080/mcp";
    private static final Map<String, String> SESSION = Map.of(
            "Mcp-Session-Id", "live", "MCP-Protocol-Version", "2024-11-05", "Authorization", "Bearer t");
    private static final ConnectResult INVENTORY = new ConnectResult(
            List.of(new McpToolDefinition("read", "", "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}")),
            List.of(new McpResourceDefinition("file:///a", "a", "", "text/plain")),
            List.of(new McpResourceTemplateDefinition("file:///{name}", "t", "", "text/plain")),
            List.of(new McpPromptDefinition("summarise", "", List.of(new PromptArgument("topic", "", true)))),
            new ServerMetadata(Map.of(), "", Map.of("tools", Map.of(), "resources", Map.of("subscribe", true),
                    "prompts", Map.of(), "completions", Map.of(), "logging", Map.of())));

    private final TemplateExpansionRegistry templateExpansions = new TemplateExpansionRegistry();
    private final GeneratedRequestPlanner planner = new GeneratedRequestPlanner(templateExpansions);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void plansEveryGeneratedFamilyThenAppendsSeeds() throws Exception {
        SeedRequest seed = new SeedRequest("tools/call",
                (ObjectNode) McpObjectMapper.INSTANCE.readTree("{\"name\":\"read\",\"arguments\":{\"path\":\"x.md\"}}"));

        List<HttpRequest> requests = planner.plan(INVENTORY, List.of(seed), ENDPOINT, SESSION, PlainFraming.framer());

        assertThat(requests).extracting(GeneratedRequestPlannerTest::method).containsExactly(
                "tools/list", "resources/list", "resources/templates/list", "prompts/list", "initialize",
                "notifications/initialized", "notifications/cancelled", "notifications/progress",
                "notifications/roots/list_changed", "completion/complete", "completion/complete", "logging/setLevel",
                "ping", "resources/subscribe", "resources/unsubscribe", "server/discover", "tools/call",
                "resources/read", "resources/read", "prompts/get", "tools/call");
        assertThat(body(requests.get(requests.size() - 1)).path("params").path("arguments").path("path").asText())
                .isEqualTo("x.md");
    }

    @Test
    void everyPlannedRequestCarriesTheSessionHeadersButLeavesTheBearerToTheProxy() {
        List<HttpRequest> requests = planner.plan(INVENTORY, List.of(), ENDPOINT, SESSION, PlainFraming.framer());

        assertThat(requests).allSatisfy(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.url()).isEqualTo(ENDPOINT);
            assertThat(request.headerValue("Mcp-Session-Id")).isEqualTo("live");
            assertThat(request.headerValue("MCP-Protocol-Version")).isEqualTo("2024-11-05");
            assertThat(request.hasHeader("Authorization")).isFalse();
            assertThat(request.headerValue("Content-Type")).isEqualTo("application/json");
        });
    }

    @Test
    void sessionWithoutCredentialsGetsNoAuthorizationHeader() {
        List<HttpRequest> requests = planner.plan(INVENTORY, List.of(), ENDPOINT, Map.of("Mcp-Session-Id", "live"),
                PlainFraming.framer());

        assertThat(requests).allSatisfy(request -> assertThat(request.headerValue("Authorization")).isNull());
    }

    @Test
    void notificationsAreRenderedWithoutAnId() throws Exception {
        HttpRequest initialized = planner.plan(INVENTORY, List.of(), ENDPOINT, SESSION, PlainFraming.framer()).stream()
                .filter(request -> method(request).equals("notifications/initialized")).findFirst().orElseThrow();

        assertThat(body(initialized).has("id")).isFalse();
    }

    @Test
    void recordsTemplateExpansionsForTheHarnessInsertionPointProvider() {
        planner.plan(INVENTORY, List.of(), ENDPOINT, SESSION, PlainFraming.framer());

        assertThat(templateExpansions.variablesIn("file:///name")).hasSize(1);
    }

    @Test
    void generatedToolCallsOnAModernSessionCarryTheirParamHeaders() throws Exception {
        ConnectResult inventory = new ConnectResult(List.of(new McpToolDefinition("lookup", "",
                "{\"type\":\"object\",\"properties\":{\"tenant\":{\"type\":\"string\",\"x-mcp-header\":\"Tenant\"}}}")),
                List.of(), List.of(), List.of(), new ServerMetadata(Map.of(), "", Map.of("tools", Map.of())));
        McpOperationFraming.Framer modern = PlainFraming.withOperations(
                (method, params, id) -> McpRequests.modernOperation(method, "burp", "1", params, id));

        HttpRequest call = planner.plan(inventory, List.of(), ENDPOINT, Map.of(),
                        InventoryFraming.withParamHeaders(modern, inventory)).stream()
                .filter(request -> method(request).equals("tools/call")).findFirst().orElseThrow();

        assertThat(call.headerValue("Mcp-Param-Tenant"))
                .isEqualTo(body(call).path("params").path("arguments").path("tenant").asText()).isNotEmpty();
    }

    private static String method(HttpRequest request) {
        try {
            return body(request).path("method").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode body(HttpRequest request) throws Exception {
        return McpObjectMapper.INSTANCE.readTree(request.body().getBytes());
    }
}
