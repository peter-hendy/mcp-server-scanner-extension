package com.mcpscanner.access;

import com.mcpscanner.mcp.ToolSafety;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolAnnotations;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InvokedToolsTest {

    private static final McpToolDefinition CATALOGUED = new McpToolDefinition("read_notes", "", "{}", List.of(),
            new ToolAnnotations(null, true, null, null, null));

    private final InvokedTools invokedTools = new InvokedTools(name -> Optional.ofNullable(
            Map.of(CATALOGUED.name(), CATALOGUED).get(name)));

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void takesTheCataloguedDefinitionOfACalledTool() {
        assertThat(invokedTools.in(List.of(toolCall("read_notes")))).containsExactly(CATALOGUED);
    }

    @Test
    void treatsAToolMissingFromTheCatalogAsUnannotated() {
        assertThat(invokedTools.in(List.of(toolCall("delete_account"))))
                .singleElement().satisfies(tool -> {
                    assertThat(tool.name()).isEqualTo("delete_account");
                    assertThat(ToolSafety.mayWrite(tool)).isTrue();
                });
    }

    @Test
    void namesEachToolOnceAndIgnoresRequestsThatCallNoTool() {
        List<HttpRequestResponse> requests = List.of(toolCall("delete_account"), toolCall("delete_account"),
                withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\",\"params\":{\"uri\":\"a://b\"}}"),
                withBody(null));

        assertThat(invokedTools.in(requests)).extracting(McpToolDefinition::name).containsExactly("delete_account");
    }

    static HttpRequestResponse toolCall(String tool) {
        return withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool
                + "\",\"arguments\":{}}}");
    }

    private static HttpRequestResponse withBody(String body) {
        HttpRequest request = HttpRequest.httpRequestFromUrl("https://mcp.example.test/mcp").withMethod("POST");
        return HttpRequestResponse.httpRequestResponse(body == null ? request : request.withBody(body), null);
    }
}
