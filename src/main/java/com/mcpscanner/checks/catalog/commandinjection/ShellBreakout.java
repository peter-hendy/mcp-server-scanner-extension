package com.mcpscanner.checks.catalog.commandinjection;

import java.util.function.BinaryOperator;

enum ShellBreakout {

    COMMAND_SUBSTITUTION((original, command) -> original + "$(" + command + ")"),
    BACKTICK((original, command) -> original + "`" + command + "`"),
    SEMICOLON((original, command) -> original + "; " + command + " #"),
    NEWLINE((original, command) -> original + "\n" + command + " #", false),
    DOUBLE_QUOTE((original, command) -> original + "\"; " + command + " #"),
    SINGLE_QUOTE((original, command) -> original + "'; " + command + " #");

    private final BinaryOperator<String> wrapping;
    private final boolean singleLine;

    ShellBreakout(BinaryOperator<String> wrapping) {
        this(wrapping, true);
    }

    ShellBreakout(BinaryOperator<String> wrapping, boolean singleLine) {
        this.wrapping = wrapping;
        this.singleLine = singleLine;
    }

    boolean fitsOnOneLine() {
        return singleLine;
    }

    String wrap(String original, String command) {
        return wrapping.apply(original, command);
    }
}
