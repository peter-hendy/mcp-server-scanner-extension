package com.mcpscanner.scan;

import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.request.RequestFamily;

import java.util.List;

public sealed interface ScanTarget {

    RequestFamily family();

    ScanInventory inventory();

    String kindLabel();

    String identifier();

    record Tool(McpToolDefinition tool) implements ScanTarget {
        @Override
        public RequestFamily family() {
            return RequestFamily.TOOLS_CALL;
        }

        @Override
        public ScanInventory inventory() {
            return ScanInventory.toolsOnly(List.of(tool));
        }

        @Override
        public String kindLabel() {
            return "tool";
        }

        @Override
        public String identifier() {
            return tool.name();
        }
    }

    record Resource(McpResourceDefinition resource) implements ScanTarget {
        @Override
        public RequestFamily family() {
            return RequestFamily.RESOURCES_READ;
        }

        @Override
        public ScanInventory inventory() {
            return new ScanInventory(List.of(), List.of(resource), List.of(), List.of());
        }

        @Override
        public String kindLabel() {
            return "resource";
        }

        @Override
        public String identifier() {
            return resource.uri();
        }
    }

    record ResourceTemplate(McpResourceTemplateDefinition template) implements ScanTarget {
        @Override
        public RequestFamily family() {
            return RequestFamily.RESOURCE_TEMPLATES_READ;
        }

        @Override
        public ScanInventory inventory() {
            return new ScanInventory(List.of(), List.of(), List.of(template), List.of());
        }

        @Override
        public String kindLabel() {
            return "resource template";
        }

        @Override
        public String identifier() {
            return template.uriTemplate();
        }
    }

    record Prompt(McpPromptDefinition prompt) implements ScanTarget {
        @Override
        public RequestFamily family() {
            return RequestFamily.PROMPTS_GET;
        }

        @Override
        public ScanInventory inventory() {
            return new ScanInventory(List.of(), List.of(), List.of(), List.of(prompt));
        }

        @Override
        public String kindLabel() {
            return "prompt";
        }

        @Override
        public String identifier() {
            return prompt.name();
        }
    }
}
