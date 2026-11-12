package com.mcpscanner.ui.widgets;

public final class Plurals {

    private Plurals() {
    }

    public static String count(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
