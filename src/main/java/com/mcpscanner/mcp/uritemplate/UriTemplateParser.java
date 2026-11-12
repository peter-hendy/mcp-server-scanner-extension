package com.mcpscanner.mcp.uritemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UriTemplateParser {

    private static final String VARIABLE_CHARACTER = "(?:[A-Za-z0-9_-]|%[0-9A-Fa-f]{2})";
    private static final Pattern VARIABLE = Pattern.compile(
            "(" + VARIABLE_CHARACTER + "(?:\\.?" + VARIABLE_CHARACTER + ")*)(?:(\\*)|:([1-9][0-9]{0,3}))?");

    private final String text;
    private final List<TemplateExpression> expressions = new ArrayList<>();

    UriTemplateParser(String text) {
        this.text = text;
    }

    Optional<UriTemplate> parse() {
        int cursor = 0;
        while (cursor >= 0 && cursor < text.length()) {
            cursor = readFrom(cursor);
        }
        return cursor < 0 ? Optional.empty() : Optional.of(new UriTemplate(text, expressions));
    }

    private int readFrom(int cursor) {
        int open = text.indexOf('{', cursor);
        int literalEnd = open < 0 ? text.length() : open;
        if (text.substring(cursor, literalEnd).indexOf('}') >= 0) {
            return -1;
        }
        return open < 0 ? text.length() : readExpression(open);
    }

    private int readExpression(int open) {
        int close = text.indexOf('}', open);
        if (close < 0) {
            return -1;
        }
        Optional<TemplateExpression> expression = expression(text.substring(open + 1, close), open, close + 1);
        expression.ifPresent(expressions::add);
        return expression.isPresent() ? close + 1 : -1;
    }

    private static Optional<TemplateExpression> expression(String body, int start, int end) {
        return TemplateOperator.leading(body).flatMap(operator ->
                variables(body.substring(operator.symbol().length()))
                        .map(variables -> new TemplateExpression(start, end, operator, variables)));
    }

    private static Optional<List<TemplateVariable>> variables(String list) {
        List<TemplateVariable> variables = new ArrayList<>();
        for (String specification : list.split(",", -1)) {
            Matcher matcher = VARIABLE.matcher(specification);
            if (!matcher.matches()) {
                return Optional.empty();
            }
            variables.add(variable(matcher));
        }
        return Optional.of(variables);
    }

    private static TemplateVariable variable(Matcher matcher) {
        OptionalInt prefix = matcher.group(3) == null
                ? OptionalInt.empty()
                : OptionalInt.of(Integer.parseInt(matcher.group(3)));
        return new TemplateVariable(matcher.group(1), matcher.group(2) != null, prefix);
    }
}
