package com.mcpscanner.access.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

final class ScriptedServer implements MiningSender {

    record Sent(String identityId, MiningCall call) {
        String toolName() {
            return call.params().path("name").asText();
        }

        JsonNode arguments() {
            return call.params().path("arguments");
        }
    }

    private final List<BiFunction<Principal, MiningCall, Optional<MiningReply>>> rules = new ArrayList<>();
    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    ScriptedServer on(BiFunction<Principal, MiningCall, Optional<MiningReply>> rule) {
        rules.add(rule);
        return this;
    }

    ScriptedServer tool(String identityId, String toolName, String structuredContent) {
        return on((identity, call) -> identity.id().equals(identityId)
                && call.method().equals("tools/call") && call.params().path("name").asText().equals(toolName)
                ? Optional.of(MiningReply.of(200, structured(structuredContent))) : Optional.empty());
    }

    @Override
    public MiningReply send(Principal identity, MiningCall call) {
        sent.add(new Sent(identity.id(), call));
        return rules.stream().map(rule -> rule.apply(identity, call)).flatMap(Optional::stream).findFirst()
                .orElse(MiningReply.of(200, json("{\"content\":[{\"type\":\"text\",\"text\":\"not found\"}]}")));
    }

    List<Sent> sent() {
        return List.copyOf(sent);
    }

    List<Sent> toolCalls(String toolName) {
        return sent.stream().filter(entry -> entry.call().method().equals("tools/call")
                && entry.toolName().equals(toolName)).toList();
    }

    static ObjectNode structured(String structuredContent) {
        ObjectNode result = McpObjectMapper.INSTANCE.createObjectNode();
        result.set("structuredContent", json(structuredContent));
        return result;
    }

    static JsonNode json(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (Exception malformed) {
            throw new IllegalArgumentException(malformed);
        }
    }

    static Principal identity(String id) {
        return new Principal(id, id, PrincipalRole.PEER, Map::of, Optional.empty(), Map.of());
    }

    static McpToolDefinition readOnlyTool(String name, String inputSchema) {
        return new McpToolDefinition(name, "", inputSchema, List.of(),
                new ToolAnnotations(null, true, null, null, null));
    }

    static McpToolDefinition unannotatedTool(String name, String inputSchema) {
        return new McpToolDefinition(name, "", inputSchema);
    }

    static String stringArgument(String name) {
        return "{\"type\":\"object\",\"properties\":{\"" + name + "\":{\"type\":\"string\"}}}";
    }

    static String noArguments() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }
}
