package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record ParamHeaderBinding(String headerName, List<String> propertyPath) {

    public ParamHeaderBinding {
        propertyPath = List.copyOf(propertyPath);
    }

    public Optional<String> valueIn(JsonNode arguments) {
        JsonNode value = arguments;
        for (String property : propertyPath) {
            value = value.path(property);
        }
        return value.isValueNode() && !value.isNull()
                ? Optional.of(ParamHeaderValue.encoded(text(value))) : Optional.empty();
    }

    private static String text(JsonNode value) {
        return value.isNumber() ? decimalText(value.decimalValue()) : value.asText();
    }

    private static String decimalText(BigDecimal number) {
        BigDecimal stripped = number.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.toBigIntegerExact().toString() : stripped.toPlainString();
    }
}
