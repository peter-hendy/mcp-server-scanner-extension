package com.mcpscanner.scan.request;

import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.PromptArgument;
import com.mcpscanner.mcp.ToolAnnotations;
import com.mcpscanner.scan.ScanInventory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SampleSurfaces {

    static final McpToolDefinition READ_TOOL = new McpToolDefinition("lookup", "d",
            "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"},"
                    + "\"limit\":{\"type\":\"integer\"},\"opts\":{\"type\":\"object\",\"properties\":"
                    + "{\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}}}",
            List.of(), new ToolAnnotations(null, true, false, null, null));
    static final McpToolDefinition WRITE_TOOL = new McpToolDefinition("delete_file", "d",
            "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}");
    static final McpResourceDefinition RESOURCE = new McpResourceDefinition("file:///notes.txt", "notes", "", "text/plain", List.of());
    static final McpResourceTemplateDefinition TEMPLATE =
            new McpResourceTemplateDefinition("file:///docs/{name}", "docs", "", "text/plain");
    static final McpPromptDefinition PROMPT = new McpPromptDefinition("greet", "",
            List.of(new PromptArgument("who", "", true), new PromptArgument("tone", "", false)), List.of());

    static final Map<String, Object> ALL_CAPABILITIES = Map.of(
            "tools", Map.of(),
            "resources", Map.of("subscribe", true),
            "prompts", Map.of(),
            "completions", Map.of(),
            "logging", Map.of());

    static final URI REGISTRATION_ENDPOINT = URI.create("https://auth.example.test/oauth/register");

    private SampleSurfaces() {}

    public static McpMessage ping() {
        return McpMessage.request(RequestFamily.PING, "ping", com.mcpscanner.mcp.McpObjectMapper.INSTANCE.createObjectNode());
    }

    public static List<McpMessage> jsonRpc(GeneratedScan scan) {
        return scan.messages().stream().filter(McpMessage.class::isInstance).map(McpMessage.class::cast).toList();
    }

    public static ScanInventory fullInventory() {
        return new ScanInventory(List.of(READ_TOOL, WRITE_TOOL), List.of(RESOURCE), List.of(TEMPLATE), List.of(PROMPT));
    }

    public static ServerSurface full() {
        return new ServerSurface(fullInventory(), new ServerCapabilities(ALL_CAPABILITIES), null,
                () -> Optional.of(REGISTRATION_ENDPOINT));
    }

    public static ServerSurface withoutRegistration() {
        return new ServerSurface(fullInventory(), new ServerCapabilities(ALL_CAPABILITIES));
    }

    public static ServerSurface advertisingRegistration() {
        return advertisingRegistration(REGISTRATION_ENDPOINT);
    }

    public static ServerSurface advertisingRegistration(URI registrationEndpoint) {
        return new ServerSurface(new ScanInventory(List.of(), List.of(), List.of(), List.of()),
                ServerCapabilities.none(), null, () -> Optional.of(registrationEndpoint));
    }
}
