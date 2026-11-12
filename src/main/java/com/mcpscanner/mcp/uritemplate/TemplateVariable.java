package com.mcpscanner.mcp.uritemplate;

import java.util.OptionalInt;

public record TemplateVariable(String name, boolean exploded, OptionalInt prefixLength) {
}
