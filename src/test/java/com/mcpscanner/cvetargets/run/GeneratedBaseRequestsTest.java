package com.mcpscanner.cvetargets.run;

import com.mcpscanner.testutil.PlainFraming;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.cvetargets.requests.DiscoveredInventoryRequestSource;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedBaseRequestsTest {

    private static final String ENDPOINT = "http://target.test:8080/mcp";
    private static final ConnectResult TEMPLATE_ONLY = new ConnectResult(List.of(), List.of(),
            List.of(new McpResourceTemplateDefinition("file:///{name}", "t", "", "text/plain")), List.of(),
            new ServerMetadata(Map.of(), "", Map.of("resources", Map.of())));

    private final GeneratedBaseRequests baseRequests = GeneratedBaseRequests.create();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void harnessSourceIsBackedByTheGeneratedPlanner() {
        assertThat(baseRequests.source()).isInstanceOf(DiscoveredInventoryRequestSource.class);
    }

    @Test
    void insertionPointsSeeTheTemplateVariablesThePlannerRecorded() {
        HttpRequest templateRead = plan(List.of()).stream()
                .filter(request -> request.bodyToString().contains("\"method\":\"resources/read\""))
                .findFirst().orElseThrow();

        assertThat(names(templateRead)).contains("MCP field: params.uri", "MCP template variable: name");
    }

    @Test
    void headerFieldsBuildPayloadRequestsOnThePlannedRequests() {
        HttpRequest ping = plan(List.of()).stream()
                .filter(request -> request.bodyToString().contains("\"method\":\"ping\"")).findFirst().orElseThrow();
        AuditInsertionPoint sessionId = points(ping).stream()
                .filter(point -> point.name().equals("MCP header: Mcp-Session-Id")).findFirst().orElseThrow();

        HttpRequest mutated = sessionId.buildHttpRequestWithPayload(ByteArray.byteArray("fuzzed"));

        assertThat(mutated).isNotNull();
        assertThat(mutated.headerValue("Mcp-Session-Id")).isEqualTo("fuzzed");
        assertThat(sessionId.issueHighlights(ByteArray.byteArray("fuzzed"))).hasSize(1);
    }

    @Test
    void generatedRequestsAndSeedsNeverShareARequestId() throws Exception {
        SeedRequest seed = new SeedRequest("tools/call",
                (ObjectNode) McpObjectMapper.INSTANCE.readTree("{\"name\":\"x\",\"arguments\":{}}"));
        Set<Long> ids = new HashSet<>();
        int requestsWithId = 0;
        for (HttpRequest request : plan(List.of(seed))) {
            var id = McpObjectMapper.INSTANCE.readTree(request.bodyToString()).path("id");
            if (!id.isMissingNode()) {
                requestsWithId++;
                ids.add(id.asLong());
            }
        }

        assertThat(ids).hasSize(requestsWithId);
    }

    private List<HttpRequest> plan(List<SeedRequest> seeds) {
        return baseRequests.planner().plan(TEMPLATE_ONLY, seeds, ENDPOINT,
                Map.of("Mcp-Session-Id", "live", "MCP-Protocol-Version", "2024-11-05"), PlainFraming.framer());
    }

    private List<String> names(HttpRequest request) {
        return points(request).stream().map(AuditInsertionPoint::name).toList();
    }

    private List<AuditInsertionPoint> points(HttpRequest request) {
        HttpRequestResponse base = mock(HttpRequestResponse.class);
        when(base.request()).thenReturn(request);
        return baseRequests.insertionPoints().insertionPointsFor(base);
    }
}
