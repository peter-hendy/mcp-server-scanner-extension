package com.mcpscanner.mcp.uritemplate;

import java.util.Arrays;
import java.util.Optional;

public enum TemplateOperator {
    SIMPLE("", "simple", "", ",", false),
    RESERVED("+", "reserved", "", ",", false),
    FRAGMENT("#", "fragment", "#", ",", false),
    LABEL(".", "label", ".", ".", false),
    PATH("/", "path", "/", "/", false),
    PATH_STYLE(";", "path-style", ";", ";", true),
    FORM_QUERY("?", "form query", "?", "&", true),
    QUERY_CONTINUATION("&", "query continuation", "&", "&", true);

    private static final String RESERVED_FOR_FUTURE = "=,!@|";

    private final String symbol;
    private final String label;
    private final String prefix;
    private final String separator;
    private final boolean named;

    TemplateOperator(String symbol, String label, String prefix, String separator, boolean named) {
        this.symbol = symbol;
        this.label = label;
        this.prefix = prefix;
        this.separator = separator;
        this.named = named;
    }

    static Optional<TemplateOperator> leading(String body) {
        if (!body.isEmpty() && RESERVED_FOR_FUTURE.indexOf(body.charAt(0)) >= 0) {
            return Optional.empty();
        }
        return Optional.of(Arrays.stream(values())
                .filter(operator -> operator != SIMPLE && body.startsWith(operator.symbol))
                .findFirst()
                .orElse(SIMPLE));
    }

    public String symbol() {
        return symbol;
    }

    public String description() {
        return this == SIMPLE ? label : label + " (" + symbol + ")";
    }

    public String prefix() {
        return prefix;
    }

    public String separator() {
        return separator;
    }

    public boolean named() {
        return named;
    }

    public boolean allowsReserved() {
        return this == RESERVED || this == FRAGMENT;
    }
}
