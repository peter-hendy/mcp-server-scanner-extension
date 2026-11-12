package com.mcpscanner.access.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.JsonSchemaDefaults;
import com.mcpscanner.scan.PromptDefaults;
import com.mcpscanner.scan.request.McpMessage;
import com.mcpscanner.scan.request.RequestFamily;

import java.util.Map;
import java.util.Optional;

public sealed interface MiningCall {

    RequestFamily family();

    String method();

    ObjectNode params();

    default String operation() {
        return method();
    }

    default McpMessage message() {
        return McpMessage.call(family(), method(), params());
    }

    record ToolCall(McpToolDefinition tool, Map<String, JsonNode> arguments) implements MiningCall {

        public ToolCall {
            arguments = Map.copyOf(arguments);
        }

        @Override
        public RequestFamily family() {
            return RequestFamily.TOOLS_CALL;
        }

        @Override
        public String method() {
            return McpMethods.TOOLS_CALL;
        }

        @Override
        public ObjectNode params() {
            ObjectNode params = McpObjectMapper.INSTANCE.createObjectNode().put("name", tool.name());
            ObjectNode values = JsonSchemaDefaults.requiredArguments(tool.inputSchema());
            arguments.forEach(values::set);
            params.set("arguments", values);
            return params;
        }

        @Override
        public String operation() {
            return method() + " " + tool.name();
        }
    }

    record ResourceRead(String uri) implements MiningCall {

        @Override
        public RequestFamily family() {
            return RequestFamily.RESOURCES_READ;
        }

        @Override
        public String method() {
            return McpMethods.RESOURCES_READ;
        }

        @Override
        public ObjectNode params() {
            return McpObjectMapper.INSTANCE.createObjectNode().put("uri", uri);
        }

        @Override
        public String operation() {
            return method() + " " + uri;
        }
    }

    record ResourceListing(Optional<String> cursor, int page) implements MiningCall {

        private static final String CURSOR = "cursor";
        private static final String NEXT_CURSOR = "nextCursor";

        public ResourceListing() {
            this(Optional.empty(), 1);
        }

        @Override
        public RequestFamily family() {
            return RequestFamily.RESOURCES_LIST;
        }

        @Override
        public String method() {
            return McpMethods.RESOURCES_LIST;
        }

        @Override
        public ObjectNode params() {
            ObjectNode params = McpObjectMapper.INSTANCE.createObjectNode();
            cursor.ifPresent(value -> params.put(CURSOR, value));
            return params;
        }

        @Override
        public String operation() {
            return page == 1 ? method() : method() + " (page " + page + ")";
        }

        Optional<ResourceListing> next(JsonNode result, int maxPages) {
            JsonNode next = result.path(NEXT_CURSOR);
            return page < maxPages && next.isTextual() && !next.asText().isBlank()
                    ? Optional.of(new ResourceListing(Optional.of(next.asText()), page + 1)) : Optional.empty();
        }
    }

    record PromptGet(McpPromptDefinition prompt) implements MiningCall {

        @Override
        public RequestFamily family() {
            return RequestFamily.PROMPTS_GET;
        }

        @Override
        public String method() {
            return McpMethods.PROMPTS_GET;
        }

        @Override
        public ObjectNode params() {
            ObjectNode params = McpObjectMapper.INSTANCE.createObjectNode().put("name", prompt.name());
            params.set("arguments", PromptDefaults.arguments(prompt));
            return params;
        }

        @Override
        public String operation() {
            return method() + " " + prompt.name();
        }
    }
}
