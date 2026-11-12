package com.mcpscanner.checks.catalog.commandinjection;

import java.util.Arrays;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

enum CharacterCodeDecoder {

    PYTHON(codes -> "__import__('builtins').bytes([" + codes + "]).decode()"),
    JAVASCRIPT(codes -> "String.fromCharCode(" + codes + ")"),
    RUBY(codes -> "[" + codes + "].pack('C*')"),
    PHP(codes -> Arrays.stream(codes.split(","))
            .map(code -> "chr(" + code + ")")
            .collect(Collectors.joining(".")));

    private final UnaryOperator<String> expression;

    CharacterCodeDecoder(UnaryOperator<String> expression) {
        this.expression = expression;
    }

    String decoding(String value) {
        return expression.apply(value.chars().mapToObj(Integer::toString).collect(Collectors.joining(",")));
    }
}
