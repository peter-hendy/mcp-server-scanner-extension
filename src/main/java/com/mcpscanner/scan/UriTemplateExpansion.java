package com.mcpscanner.scan;

import com.mcpscanner.mcp.uritemplate.TemplateExpression;
import com.mcpscanner.mcp.uritemplate.TemplateOperator;
import com.mcpscanner.mcp.uritemplate.TemplateVariable;
import com.mcpscanner.mcp.uritemplate.UriTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UriTemplateExpansion {

    private static final String RESERVED = ":/?#[]@!$&'()*+,;=";

    private UriTemplateExpansion() {}

    public record Variable(String name, int startInclusive, int endExclusive) {}

    public record Result(String expandedUri, List<Variable> variables) {}

    public static Result expand(String template) {
        return expanded(template, (name, operator) -> name);
    }

    public static String expandWith(String template, Map<String, String> values) {
        return expanded(template, (name, operator) -> values.containsKey(name)
                ? encoded(values.get(name), operator.allowsReserved()) : name).expandedUri();
    }

    public static Optional<Map<String, String>> match(String template, String uri) {
        Optional<UriTemplate> parsed = UriTemplate.parse(template);
        List<String> names = parsed.map(UriTemplateExpansion::names).orElse(List.of());
        if (names.isEmpty()) {
            return Optional.empty();
        }
        Matcher matched = Pattern.compile(pattern(parsed.get())).matcher(uri);
        if (!matched.matches()) {
            return Optional.empty();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < names.size(); index++) {
            values.putIfAbsent(names.get(index), decoded(matched.group(index + 1)));
        }
        return Optional.of(values);
    }

    private static Result expanded(String template, BiFunction<String, TemplateOperator, String> value) {
        return UriTemplate.parse(template).map(parsed -> rendered(parsed, value))
                .orElseGet(() -> new Result(template == null ? "" : template, List.of()));
    }

    private static Result rendered(UriTemplate template, BiFunction<String, TemplateOperator, String> value) {
        StringBuilder expanded = new StringBuilder(template.text().length());
        List<Variable> variables = new ArrayList<>();
        int cursor = 0;
        for (TemplateExpression expression : template.expressions()) {
            expanded.append(template.text(), cursor, expression.start());
            renderExpression(expression, expanded, variables, value);
            cursor = expression.end();
        }
        expanded.append(template.text().substring(cursor));
        return new Result(expanded.toString(), List.copyOf(variables));
    }

    private static void renderExpression(TemplateExpression expression, StringBuilder expanded,
                                         List<Variable> variables,
                                         BiFunction<String, TemplateOperator, String> value) {
        TemplateOperator operator = expression.operator();
        expanded.append(operator.prefix());
        for (int index = 0; index < expression.variables().size(); index++) {
            String name = expression.variables().get(index).name();
            expanded.append(index == 0 ? "" : operator.separator()).append(operator.named() ? name + "=" : "");
            int byteStart = utf8ByteLength(expanded);
            expanded.append(value.apply(name, operator));
            variables.add(new Variable(name, byteStart, utf8ByteLength(expanded)));
        }
    }

    private static List<String> names(UriTemplate template) {
        return template.expressions().stream().flatMap(expression -> expression.variables().stream())
                .map(TemplateVariable::name).toList();
    }

    private static String pattern(UriTemplate template) {
        StringBuilder regex = new StringBuilder();
        int cursor = 0;
        for (TemplateExpression expression : template.expressions()) {
            regex.append(Pattern.quote(template.text().substring(cursor, expression.start())));
            regex.append(expressionPattern(expression));
            cursor = expression.end();
        }
        return regex.append(Pattern.quote(template.text().substring(cursor))).toString();
    }

    private static String expressionPattern(TemplateExpression expression) {
        TemplateOperator operator = expression.operator();
        StringBuilder regex = new StringBuilder(Pattern.quote(operator.prefix()));
        for (int index = 0; index < expression.variables().size(); index++) {
            String name = expression.variables().get(index).name();
            regex.append(index == 0 ? "" : Pattern.quote(operator.separator()))
                    .append(operator.named() ? Pattern.quote(name + "=") : "")
                    .append(valuePattern(operator));
        }
        return regex.toString();
    }

    private static String valuePattern(TemplateOperator operator) {
        if (operator.allowsReserved()) {
            return "(.+?)";
        }
        return "([^/?#\\" + operator.separator() + "]+)";
    }

    private static String encoded(String value, boolean allowReserved) {
        StringBuilder encoded = new StringBuilder();
        for (byte octet : value.getBytes(StandardCharsets.UTF_8)) {
            char character = (char) (octet & 0xFF);
            if (unreserved(character) || allowReserved && RESERVED.indexOf(character) >= 0) {
                encoded.append(character);
            } else {
                encoded.append('%').append(String.format("%02X", octet & 0xFF));
            }
        }
        return encoded.toString();
    }

    private static boolean unreserved(char character) {
        return character < 0x80 && (Character.isLetterOrDigit(character) || "-._~".indexOf(character) >= 0);
    }

    private static String decoded(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return value;
        }
    }

    private static int utf8ByteLength(CharSequence text) {
        return text.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
