package com.mcpscanner.mcp.uritemplate;

import java.util.List;

public record TemplateExpression(int start, int end, TemplateOperator operator, List<TemplateVariable> variables) {

    public TemplateExpression {
        variables = List.copyOf(variables);
    }
}
