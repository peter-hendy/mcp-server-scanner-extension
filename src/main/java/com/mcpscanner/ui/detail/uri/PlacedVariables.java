package com.mcpscanner.ui.detail.uri;

import com.mcpscanner.mcp.uritemplate.TemplateExpression;
import com.mcpscanner.mcp.uritemplate.TemplateOperator;
import com.mcpscanner.mcp.uritemplate.TemplateVariable;
import com.mcpscanner.mcp.uritemplate.UriTemplate;

import java.util.ArrayList;
import java.util.List;

public final class PlacedVariables {

    private PlacedVariables() {
    }

    public static List<PlacedVariable> of(UriTemplate template) {
        String text = template.text();
        UriLocation location = UriLocation.startOf(text);
        List<PlacedVariable> placed = new ArrayList<>();
        int cursor = UriLocation.afterScheme(text);
        for (TemplateExpression expression : template.expressions()) {
            text.substring(Math.min(cursor, expression.start()), expression.start()).chars()
                    .forEach(literal -> location.pass((char) literal));
            location.enter(expression);
            placed.addAll(placedIn(expression, location));
            location.leave(expression);
            cursor = expression.end();
        }
        return List.copyOf(placed);
    }

    private static List<PlacedVariable> placedIn(TemplateExpression expression, UriLocation location) {
        List<TemplateVariable> variables = expression.variables();
        List<PlacedVariable> placed = new ArrayList<>();
        for (int index = 0; index < variables.size(); index++) {
            TemplateVariable variable = variables.get(index);
            placed.add(new PlacedVariable(variable.name(), location.positionOf(expression.operator(), index),
                    expansionOf(expression.operator(), variable)));
        }
        return placed;
    }

    private static String expansionOf(TemplateOperator operator, TemplateVariable variable) {
        StringBuilder expansion = new StringBuilder(operator.description());
        if (variable.exploded()) {
            expansion.append(", exploded (*)");
        }
        variable.prefixLength().ifPresent(length -> expansion.append(", prefix (:").append(length).append(')'));
        return expansion.toString();
    }
}
