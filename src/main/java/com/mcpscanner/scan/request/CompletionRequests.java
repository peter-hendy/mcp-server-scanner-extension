package com.mcpscanner.scan.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.scan.UriTemplateExpansion;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class CompletionRequests implements RequestFamilyBuilder {

    static final String PARTIAL_VALUE = "a";

    @Override
    public RequestFamily family() {
        return RequestFamily.COMPLETION;
    }

    @Override
    public boolean supportedBy(ServerSurface surface) {
        return surface.capabilities().advertises("completions");
    }

    @Override
    public List<McpMessage> build(ServerSurface surface) {
        return Stream.concat(
                surface.inventory().prompts().stream().map(CompletionRequests::promptCompletion).flatMap(Optional::stream),
                surface.inventory().resourceTemplates().stream().map(CompletionRequests::templateCompletion)
                        .flatMap(Optional::stream))
                .toList();
    }

    private static Optional<McpMessage> promptCompletion(McpPromptDefinition prompt) {
        return prompt.arguments().stream().findFirst().map(argument -> {
            ObjectNode params = McpParams.empty();
            params.putObject("ref").put("type", "ref/prompt").put("name", prompt.name());
            return completion(params, argument.name());
        });
    }

    private static Optional<McpMessage> templateCompletion(McpResourceTemplateDefinition template) {
        return UriTemplateExpansion.expand(template.uriTemplate()).variables().stream().findFirst().map(variable -> {
            ObjectNode params = McpParams.empty();
            params.putObject("ref").put("type", "ref/resource").put("uri", template.uriTemplate());
            return completion(params, variable.name());
        });
    }

    private static McpMessage completion(ObjectNode params, String argumentName) {
        params.putObject("argument").put("name", argumentName).put("value", PARTIAL_VALUE);
        params.putObject("context").putObject("arguments");
        return McpMessage.request(RequestFamily.COMPLETION, McpMethods.COMPLETION_COMPLETE, McpParams.withMeta(params));
    }
}
