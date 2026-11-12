package com.mcpscanner.integration;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.requests.BaseRequestSet;
import com.mcpscanner.cvetargets.requests.BaseRequestScope;
import com.mcpscanner.cvetargets.requests.DiscoveredInventoryRequestSource;
import com.mcpscanner.cvetargets.requests.GeneratedRequestPlanner;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.scan.field.TemplateExpansionRegistry;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class GeneratedRequestSourceIT {

    @BeforeAll
    static void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void harnessSourceSendsEveryStatelessFamilyAndEveryRequestIsAnswered() throws Exception {
        assertEveryRequestAnswered(List.of(), Set.of(
                "completion/complete", "notifications/cancelled", "notifications/progress",
                "notifications/roots/list_changed", "ping", "prompts/get", "prompts/list", "resources/list",
                "resources/read", "resources/subscribe", "resources/templates/list", "resources/unsubscribe",
                "server/discover", "tools/call", "tools/list"));
    }

    @Test
    void harnessSourceSendsEveryHandshakeFamilyAndEveryRequestIsAnswered() throws Exception {
        assertEveryRequestAnswered(List.of("--protocol-version", "2025-11-25"), Set.of(
                "completion/complete", "initialize", "logging/setLevel", "notifications/cancelled",
                "notifications/initialized", "notifications/progress", "notifications/roots/list_changed",
                "ping", "prompts/get", "prompts/list", "resources/list", "resources/read",
                "resources/subscribe", "resources/templates/list", "resources/unsubscribe", "server/discover",
                "tools/call", "tools/list"));
    }

    private static void assertEveryRequestAnswered(List<String> versionArguments, Set<String> expectedMethods)
            throws Exception {
        List<String> arguments = new java.util.ArrayList<>(
                List.of("--default-mode", "safe", "--vuln", "unauthenticated_execution=vulnerable"));
        arguments.addAll(versionArguments);
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(arguments.toArray(String[]::new))) {
            TemplateExpansionRegistry templateExpansions = new TemplateExpansionRegistry();
            ArmEndpoint endpoint = new ArmEndpoint(URI.create(McpCheckItSupport.mcpEndpoint(server.port())),
                    new NoAuthStrategy());

            try (BaseRequestSet requests = new DiscoveredInventoryRequestSource(
                    new GeneratedRequestPlanner(templateExpansions)).open(endpoint, BaseRequestScope.unlimited(List.of()), new VerbatimHeaderHttp())) {
                assertThat(requests.unansweredCount()).isZero();
                assertThat(methods(requests.requests())).isEqualTo(new TreeSet<>(expectedMethods));
                assertThat(requests.requests()).allSatisfy(exchange ->
                        assertThat(exchange.response().statusCode()).isIn((short) 200, (short) 202));
                assertThat(templateExpansions.variablesIn("users://user_id/profile")).isNotEmpty();
            }
        }
    }

    private static Set<String> methods(List<HttpRequestResponse> exchanges) throws Exception {
        Set<String> methods = new TreeSet<>();
        for (HttpRequestResponse exchange : exchanges) {
            methods.add(McpObjectMapper.INSTANCE.readTree(exchange.request().bodyToString()).path("method").asText());
        }
        return methods;
    }
}
