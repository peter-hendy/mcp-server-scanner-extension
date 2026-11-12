package com.mcpscanner.mcp.uritemplate;

import java.util.List;
import java.util.Optional;

public record UriTemplate(String text, List<TemplateExpression> expressions) {

    public UriTemplate {
        expressions = List.copyOf(expressions);
    }

    public static Optional<UriTemplate> parse(String text) {
        return text == null ? Optional.empty() : new UriTemplateParser(text).parse();
    }
}
