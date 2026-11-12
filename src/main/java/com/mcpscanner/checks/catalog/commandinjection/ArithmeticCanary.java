package com.mcpscanner.checks.catalog.commandinjection;

record ArithmeticCanary(long left, long right) {

    private static final String PREFIX = "mcp";

    String result() {
        return PREFIX + (left + right);
    }

    String command() {
        return "printf " + PREFIX + "%s " + expansion();
    }

    String quotedCommand() {
        return "printf " + PREFIX + "%s '" + expansion() + "'";
    }

    String quotedLiteral() {
        return PREFIX + expansion();
    }

    private String expansion() {
        return "$((" + left + "+" + right + "))";
    }
}
