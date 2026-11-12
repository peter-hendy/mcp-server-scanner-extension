package com.mcpscanner.checks.catalog.commandinjection;

import java.util.function.BinaryOperator;

enum InterpreterBreakout {

    PYTHON_SINGLE_QUOTE("Python single-quoted string breakout", CharacterCodeDecoder.PYTHON, concatenation("'", "+")),
    PYTHON_DOUBLE_QUOTE("Python double-quoted string breakout", CharacterCodeDecoder.PYTHON,
            concatenation("\"", "+")),
    JAVASCRIPT_SINGLE_QUOTE("JavaScript single-quoted string breakout", CharacterCodeDecoder.JAVASCRIPT,
            concatenation("'", "+")),
    JAVASCRIPT_DOUBLE_QUOTE("JavaScript double-quoted string breakout", CharacterCodeDecoder.JAVASCRIPT,
            concatenation("\"", "+")),
    JAVASCRIPT_TEMPLATE("JavaScript template literal interpolation", CharacterCodeDecoder.JAVASCRIPT,
            (original, decoder) -> original + "${" + decoder + "}"),
    RUBY_SINGLE_QUOTE("Ruby single-quoted string breakout", CharacterCodeDecoder.RUBY, concatenation("'", "+")),
    RUBY_DOUBLE_QUOTE("Ruby double-quoted string breakout", CharacterCodeDecoder.RUBY, concatenation("\"", "+")),
    PHP_SINGLE_QUOTE("PHP single-quoted string breakout", CharacterCodeDecoder.PHP, concatenation("'", ".")),
    PHP_DOUBLE_QUOTE("PHP double-quoted string breakout", CharacterCodeDecoder.PHP, concatenation("\"", "."));

    private final String label;
    private final CharacterCodeDecoder decoder;
    private final BinaryOperator<String> splice;

    InterpreterBreakout(String label, CharacterCodeDecoder decoder, BinaryOperator<String> splice) {
        this.label = label;
        this.decoder = decoder;
        this.splice = splice;
    }

    String label() {
        return label;
    }

    String payload(String original, String value) {
        return splice.apply(original, decoder.decoding(value));
    }

    String bareDecoder(String value) {
        return decoder.decoding(value);
    }

    private static BinaryOperator<String> concatenation(String quote, String operator) {
        return (original, decoder) -> original + quote + operator + decoder + operator + quote;
    }
}
