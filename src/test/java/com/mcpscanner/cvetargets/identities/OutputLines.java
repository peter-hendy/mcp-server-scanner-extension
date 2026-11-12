package com.mcpscanner.cvetargets.identities;

final class OutputLines {

    private static final int TAIL_CHARACTERS = 2000;

    private OutputLines() {
    }

    static String last(String output) {
        String[] lines = output.strip().split("\\R");
        return lines[lines.length - 1];
    }

    static String tail(String output) {
        String stripped = output.strip();
        return stripped.substring(Math.max(0, stripped.length() - TAIL_CHARACTERS));
    }
}
