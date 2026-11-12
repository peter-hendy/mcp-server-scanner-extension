package com.mcpscanner.scan;

import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.PromptArgument;
import com.mcpscanner.scan.request.RequestFamily;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ScanMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanTargetTest {

    private static final McpToolDefinition TOOL = new McpToolDefinition("echo", "",
            "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}");
    private static final McpResourceDefinition RESOURCE = new McpResourceDefinition("file:///a", "a", "", "text/plain");
    private static final McpResourceTemplateDefinition TEMPLATE =
            new McpResourceTemplateDefinition("file:///{path}", "files", "", "text/plain");
    private static final McpPromptDefinition PROMPT =
            new McpPromptDefinition("summarize", "", List.of(new PromptArgument("text", "", true)));

    @Test
    void eachTargetDescribesItselfForTheAuditLog() {
        assertThat(describe(new ScanTarget.Tool(TOOL))).isEqualTo("tool: echo");
        assertThat(describe(new ScanTarget.Resource(RESOURCE))).isEqualTo("resource: file:///a");
        assertThat(describe(new ScanTarget.ResourceTemplate(TEMPLATE))).isEqualTo("resource template: file:///{path}");
        assertThat(describe(new ScanTarget.Prompt(PROMPT))).isEqualTo("prompt: summarize");
    }

    @Test
    void eachTargetIsTheSingleItemOfItsRequestFamily() {
        assertThat(new ScanTarget.Tool(TOOL).family()).isEqualTo(RequestFamily.TOOLS_CALL);
        assertThat(new ScanTarget.Tool(TOOL).inventory()).isEqualTo(ScanInventory.toolsOnly(List.of(TOOL)));
        assertThat(new ScanTarget.Resource(RESOURCE).inventory().resources()).containsExactly(RESOURCE);
        assertThat(new ScanTarget.ResourceTemplate(TEMPLATE).inventory().resourceTemplates()).containsExactly(TEMPLATE);
        assertThat(new ScanTarget.Prompt(PROMPT).inventory().prompts()).containsExactly(PROMPT);
    }

    @Test
    void eachTargetGeneratesExactlyOneRequestForItsItem() {
        assertThat(subjects(new ScanTarget.Tool(TOOL))).containsExactly("tools/call echo");
        assertThat(subjects(new ScanTarget.Resource(RESOURCE))).containsExactly("resources/read file:///a");
        assertThat(subjects(new ScanTarget.Prompt(PROMPT))).containsExactly("prompts/get summarize");
        assertThat(subjects(new ScanTarget.ResourceTemplate(TEMPLATE))).hasSize(1)
                .allSatisfy(subject -> assertThat(subject).startsWith("resources/read file:///").doesNotContain("{path}"));
    }

    private static String describe(ScanTarget target) {
        return target.kindLabel() + ": " + target.identifier();
    }

    private static List<String> subjects(ScanTarget target) {
        return RequestGenerator.standard().scanThisRequest(target.family(), target.inventory()).messages().stream()
                .map(ScanMessage::subject)
                .toList();
    }
}
