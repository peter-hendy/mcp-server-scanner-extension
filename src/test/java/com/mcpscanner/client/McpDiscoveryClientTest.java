package com.mcpscanner.client;

import com.mcpscanner.config.ResourceCap;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.core.protocol.McpResponses.Reply;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpDiscoveryClientTest {
    private final McpScannerSession session = mock(McpScannerSession.class);
    private final McpDiscoveryClient discovery = new McpDiscoveryClient(session);

    @Test
    void maps_paginated_tools_without_losing_schema_annotations_or_icons() {
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string", "pattern", "^[a-z]+$")), "additionalProperties", false);
        when(session.catalog("tools/list", null)).thenReturn(reply(Map.of("tools", List.of(Map.of("name", "read", "description", "Read", "inputSchema", schema, "annotations", Map.of("readOnlyHint", true, "title", "Reader"), "icons", List.of(Map.of("src", "https://example.test/read.svg", "mimeType", "image/svg+xml", "sizes", List.of("any"))))), "nextCursor", "p2")));
        when(session.catalog("tools/list", "p2")).thenReturn(reply(Map.of("tools", List.of(Map.of("name", "write", "inputSchema", Map.of("type", "object"))))));

        List<McpToolDefinition> tools = discovery.discoverTools();

        assertThat(tools).extracting(McpToolDefinition::name).containsExactly("read", "write");
        assertThat(tools.get(0).inputSchema()).contains("\"pattern\":\"^[a-z]+$\"");
        assertThat(tools.get(0).annotations().readOnlyHint()).isTrue();
        assertThat(tools.get(0).icons().get(0).src()).isEqualTo("https://example.test/read.svg");
    }

    @Test
    void maps_legacy_resources_templates_and_prompts() {
        when(session.catalog("resources/list", null)).thenReturn(reply(Map.of("resources", List.of(Map.of("uri", "file:///x", "name", "x", "mimeType", "text/plain")))));
        when(session.catalog("resources/templates/list", null)).thenReturn(reply(Map.of("resourceTemplates", List.of(Map.of("uriTemplate", "file:///{id}", "name", "template", "icons", List.of(Map.of("src", "https://example.test/template.svg")))))));
        when(session.catalog("prompts/list", null)).thenReturn(reply(Map.of("prompts", List.of(Map.of("name", "ask", "arguments", List.of(Map.of("name", "query", "required", true)), "icons", List.of(Map.of("src", "data:image/png;base64,AA==")))))));

        assertThat(discovery.discoverResources().get(0).uri()).isEqualTo("file:///x");
        assertThat(discovery.discoverResourceTemplates().get(0).uriTemplate()).isEqualTo("file:///{id}");
        assertThat(discovery.discoverResourceTemplates().get(0).icons()).hasSize(1);
        assertThat(discovery.discoverPrompts().get(0).arguments().get(0).required()).isTrue();
        assertThat(discovery.discoverPrompts().get(0).icons()).hasSize(1);
    }

    @Test
    void optional_list_error_keeps_no_items_and_carries_the_json_rpc_error() {
        when(session.catalog("prompts/list", null)).thenReturn(new Reply(1L, null,
                Map.of("code", -32601, "message", "Method not found")));

        ListOutcome<McpPromptDefinition> outcome = discovery.listPrompts();

        assertThat(outcome.items()).isEmpty();
        assertThat(outcome.error()).contains(new ListError.JsonRpcError(-32601, "Method not found"));
        assertThat(outcome.truncation()).isEmpty();
    }

    @Test
    void optional_list_http_status_is_reported_as_the_status() {
        when(session.catalog("resources/list", null)).thenThrow(new McpHttpStatusException("list", 403));

        assertThat(discovery.listResources().error()).contains(new ListError.HttpStatus(403));
    }

    @Test
    void optional_list_transport_failure_names_the_deepest_cause() {
        when(session.catalog("resources/templates/list", null)).thenThrow(
                new IllegalStateException("MCP request failed", new IOException("Connection reset")));

        assertThat(discovery.listResourceTemplates().error())
                .contains(new ListError.TransportFailure("Connection reset"));
    }

    @Test
    void optional_list_reply_without_the_list_field_is_an_invalid_reply() {
        when(session.catalog("resources/list", null)).thenReturn(reply(Map.of("other", List.of())));

        assertThat(discovery.listResources().error()).contains(new ListError.InvalidReply("returned no resources"));
    }

    @Test
    void optional_list_error_on_a_later_page_keeps_the_items_already_collected() {
        when(session.catalog("prompts/list", null)).thenReturn(reply(Map.of(
                "prompts", List.of(Map.of("name", "first")), "nextCursor", "p2")));
        when(session.catalog("prompts/list", "p2")).thenThrow(new McpHttpStatusException("list", 500));

        ListOutcome<McpPromptDefinition> outcome = discovery.listPrompts();

        assertThat(outcome.items()).extracting(McpPromptDefinition::name).containsExactly("first");
        assertThat(outcome.error()).contains(new ListError.HttpStatus(500));
    }

    @Test
    void optional_list_failure_is_logged_as_a_warning() {
        McpEventLog eventLog = new McpEventLog(null);
        McpDiscoveryClient logged = cappedDiscovery(eventLog, ResourceCap.DISCOVERY_MAX_PAGES, 100);
        when(session.catalog("prompts/list", null)).thenThrow(new McpHttpStatusException("list", 500));

        logged.listPrompts();

        assertThat(eventLog.snapshot()).anySatisfy(entry -> {
            assertThat(entry.message()).isEqualTo("prompts/list failed: HTTP 500");
            assertThat(entry.level()).isEqualTo(McpEventLog.Level.WARN);
        });
    }

    @Test
    void method_not_found_on_an_optional_list_is_logged_as_info_not_a_warning() {
        McpEventLog eventLog = new McpEventLog(null);
        McpDiscoveryClient logged = cappedDiscovery(eventLog, ResourceCap.DISCOVERY_MAX_PAGES, 100);
        when(session.catalog("prompts/list", null)).thenReturn(new Reply(1L, null,
                Map.of("code", -32601, "message", "Method not found")));

        logged.listPrompts();

        assertThat(eventLog.snapshot()).anySatisfy(entry -> {
            assertThat(entry.message()).isEqualTo("prompts/list failed: JSON-RPC error -32601: Method not found");
            assertThat(entry.level()).isEqualTo(McpEventLog.Level.INFO);
        });
    }

    @Test
    void tools_list_error_fails_with_the_error_detail() {
        when(session.catalog("tools/list", null)).thenReturn(new Reply(1L, null,
                Map.of("code", -32603, "message", "boom")));

        assertThatThrownBy(discovery::listTools).isInstanceOf(IllegalStateException.class)
                .hasMessage("tools/list failed: JSON-RPC error -32603: boom");
    }

    @Test
    void repeated_cursor_keeps_the_items_collected_and_names_the_cursor() {
        when(session.catalog("tools/list", null)).thenReturn(reply(Map.of(
                "tools", List.of(Map.of("name", "a")), "nextCursor", "same")));
        when(session.catalog("tools/list", "same")).thenReturn(reply(Map.of(
                "tools", List.of(Map.of("name", "b")), "nextCursor", "same")));

        ListOutcome<McpToolDefinition> outcome = discovery.listTools();

        assertThat(outcome.items()).extracting(McpToolDefinition::name).containsExactly("a", "b");
        assertThat(outcome.truncation()).contains(new ListTruncation.RepeatedCursor("same"));
        assertThat(outcome.truncation().orElseThrow().describe()).isEqualTo("stopped on repeated cursor \"same\"");
    }

    @Test
    void discover_catalog_carries_each_list_outcome() {
        when(session.catalog("tools/list", null)).thenReturn(reply(Map.of("tools", List.of(Map.of("name", "a")))));
        when(session.catalog("resources/list", null)).thenThrow(new McpHttpStatusException("list", 404));
        when(session.catalog("resources/templates/list", null)).thenReturn(reply(Map.of("resourceTemplates", List.of())));
        when(session.catalog("prompts/list", null)).thenReturn(reply(Map.of("prompts", List.of())));

        ConnectResult result = discovery.discoverCatalog();

        assertThat(result.tools()).extracting(McpToolDefinition::name).containsExactly("a");
        assertThat(result.resourcesOutcome().error()).contains(new ListError.HttpStatus(404));
        assertThat(result.resources()).isEmpty();
        assertThat(result.promptsOutcome().error()).isEmpty();
    }

    @Test
    void page_cap_stops_pagination_keeps_gathered_tools_and_warns() {
        McpEventLog eventLog = new McpEventLog(null);
        McpDiscoveryClient capped = cappedDiscovery(eventLog, ResourceCap.DISCOVERY_MAX_PAGES, 2);
        stubEndlessToolPages();

        ListOutcome<McpToolDefinition> outcome = capped.listTools();

        assertThat(outcome.items()).extracting(McpToolDefinition::name).containsExactly("tool-0", "tool-1");
        assertThat(outcome.truncation()).contains(new ListTruncation.CapReached(ResourceCap.DISCOVERY_MAX_PAGES, 2));
        assertThat(outcome.truncation().orElseThrow().describe())
                .isEqualTo("reached the \"" + ResourceCap.DISCOVERY_MAX_PAGES.label() + "\" limit of 2; raise it in Settings to discover more");
        verify(session, times(2)).catalog(eq("tools/list"), any());
        assertThat(eventLog.snapshot()).anySatisfy(entry -> assertThat(entry.message())
                .contains("tools/list").contains(ResourceCap.DISCOVERY_MAX_PAGES.label()));
    }

    @Test
    void byte_cap_stops_pagination_keeps_gathered_tools_and_warns() {
        McpEventLog eventLog = new McpEventLog(null);
        McpDiscoveryClient capped = cappedDiscovery(eventLog, ResourceCap.DISCOVERY_TOTAL_BYTES, 10);
        stubEndlessToolPages();

        List<McpToolDefinition> tools = capped.discoverTools();

        assertThat(tools).extracting(McpToolDefinition::name).containsExactly("tool-0");
        assertThat(eventLog.snapshot()).anySatisfy(entry -> assertThat(entry.message())
                .contains("tools/list").contains(ResourceCap.DISCOVERY_TOTAL_BYTES.label()));
    }

    private McpDiscoveryClient cappedDiscovery(McpEventLog eventLog, ResourceCap limited, long value) {
        return new McpDiscoveryClient(session, eventLog,
                cap -> cap == limited ? value : cap.defaultValue());
    }

    private void stubEndlessToolPages() {
        when(session.catalog(eq("tools/list"), any())).thenAnswer(invocation -> {
            String cursor = invocation.getArgument(1);
            int page = cursor == null ? 0 : Integer.parseInt(cursor);
            return reply(Map.of("tools", List.of(Map.of("name", "tool-" + page, "inputSchema", Map.of("type", "object"))),
                    "nextCursor", Integer.toString(page + 1)));
        });
    }

    @Test
    void reads_modern_server_identity_from_admission_metadata() {
        when(session.admissionEnvelope()).thenReturn(Map.of("result", Map.of("_meta", Map.of("io.modelcontextprotocol/serverInfo", Map.of("name", "modern", "version", "2", "icons", List.of(Map.of("src", "https://example.test/server.svg")))), "instructions", "Follow instructions", "capabilities", Map.of("tools", Map.of()))));

        var metadata = discovery.fetchServerMetadata();

        assertThat(metadata.serverInfo()).containsEntry("name", "modern");
        assertThat(metadata.instructions()).isEqualTo("Follow instructions");
        assertThat(metadata.capabilities()).containsKey("tools");
        assertThat(metadata.icons()).hasSize(1);
    }

    private static Reply reply(Map<String, Object> result) {
        return new Reply(1L, result, Map.of());
    }
}
