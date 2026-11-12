package com.mcpscanner.ui.detail.uri;

import com.mcpscanner.mcp.uritemplate.TemplateExpression;
import com.mcpscanner.mcp.uritemplate.TemplateOperator;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

final class UriLocation {

    private static final Set<String> HOST_SCHEMES = Set.of("http", "https", "ws", "wss", "ftp");

    private UriPart part = UriPart.PATH;
    private int segment;

    static UriLocation startOf(String template) {
        UriLocation location = new UriLocation();
        Matcher scheme = UriScheme.LEADING.matcher(template);
        if (scheme.find() && scheme.group(2) != null && HOST_SCHEMES.contains(scheme.group(1).toLowerCase(Locale.ROOT))) {
            location.part = UriPart.HOST;
        }
        return location;
    }

    static int afterScheme(String template) {
        Matcher scheme = UriScheme.LEADING.matcher(template);
        return scheme.find() ? scheme.end() : 0;
    }

    void pass(char literal) {
        if (literal == '#') {
            part = UriPart.FRAGMENT;
        } else if (literal == '?' && isBeforeQuery()) {
            part = UriPart.QUERY;
        } else if (isAuthority()) {
            passAuthority(literal);
        } else if (part == UriPart.PATH) {
            passPath(literal);
        }
    }

    String positionOf(TemplateOperator operator, int index) {
        return switch (operator) {
            case FORM_QUERY, QUERY_CONTINUATION -> isFragment() ? "fragment" : "query";
            case FRAGMENT -> "fragment";
            case PATH -> segmentName(segment + 1 + index);
            case PATH_STYLE -> "path parameter";
            default -> currentPosition();
        };
    }

    void enter(TemplateExpression expression) {
        if (expression.operator() == TemplateOperator.PATH && isAuthority()) {
            part = UriPart.PATH;
        }
        if (part == UriPart.PATH && segment == 0 && expression.operator() != TemplateOperator.PATH) {
            segment = 1;
        }
    }

    void leave(TemplateExpression expression) {
        switch (expression.operator()) {
            case PATH -> segment += expression.variables().size();
            case FORM_QUERY, QUERY_CONTINUATION -> part = isFragment() ? part : UriPart.QUERY;
            case FRAGMENT -> part = UriPart.FRAGMENT;
            default -> {
            }
        }
    }

    private String currentPosition() {
        return switch (part) {
            case HOST -> "host";
            case PORT -> "port";
            case PATH -> segmentName(segment);
            case QUERY -> "query";
            case FRAGMENT -> "fragment";
        };
    }

    private void passAuthority(char literal) {
        if (literal == '/') {
            part = UriPart.PATH;
            segment = 1;
        } else if (literal == ':') {
            part = UriPart.PORT;
        }
    }

    private void passPath(char literal) {
        if (literal == '/') {
            segment++;
        } else if (segment == 0) {
            segment = 1;
        }
    }

    private boolean isAuthority() {
        return part == UriPart.HOST || part == UriPart.PORT;
    }

    private boolean isBeforeQuery() {
        return isAuthority() || part == UriPart.PATH;
    }

    private boolean isFragment() {
        return part == UriPart.FRAGMENT;
    }

    private static String segmentName(int segment) {
        return "path segment " + Math.max(segment, 1);
    }
}
