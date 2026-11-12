package com.mcpscanner.checks.catalog.commandinjection;

import java.util.function.UnaryOperator;

enum EvaluatorFamily {

    PYTHON_EXPRESSION("Python expression", CharacterCodeDecoder.PYTHON, UnaryOperator.identity()),
    JAVASCRIPT_EXPRESSION("JavaScript expression", CharacterCodeDecoder.JAVASCRIPT, UnaryOperator.identity()),
    RUBY_EXPRESSION("Ruby expression", CharacterCodeDecoder.RUBY, UnaryOperator.identity()),
    PHP_EXPRESSION("PHP expression", CharacterCodeDecoder.PHP, decoder -> "return " + decoder + ";"),
    PYTHON_STATEMENT("Python statement", CharacterCodeDecoder.PYTHON, decoder -> "print(" + decoder + ")"),
    JAVASCRIPT_STATEMENT("JavaScript statement", CharacterCodeDecoder.JAVASCRIPT,
            decoder -> "console.log(" + decoder + ");"),
    RUBY_STATEMENT("Ruby statement", CharacterCodeDecoder.RUBY, decoder -> "puts " + decoder),
    PHP_STATEMENT("PHP statement", CharacterCodeDecoder.PHP, decoder -> "echo " + decoder + ";"),
    JAVASCRIPT_TEMPLATE("JavaScript template literal", CharacterCodeDecoder.JAVASCRIPT,
            decoder -> "${" + decoder + "}", payload -> "\\" + payload);

    private final String label;
    private final CharacterCodeDecoder decoder;
    private final UnaryOperator<String> form;
    private final UnaryOperator<String> literalControl;

    EvaluatorFamily(String label, CharacterCodeDecoder decoder, UnaryOperator<String> form) {
        this(label, decoder, form, payload -> form.apply(stringLiteral(payload)));
    }

    EvaluatorFamily(String label, CharacterCodeDecoder decoder, UnaryOperator<String> form,
                    UnaryOperator<String> literalControl) {
        this.label = label;
        this.decoder = decoder;
        this.form = form;
        this.literalControl = literalControl;
    }

    String label() {
        return label;
    }

    String payload(String value) {
        return form.apply(decoder.decoding(value));
    }

    String literalControl(String payload) {
        return literalControl.apply(payload);
    }

    private static String stringLiteral(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
