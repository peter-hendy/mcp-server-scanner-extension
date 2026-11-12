package com.mcpscanner.logging;

public final class Counted {

    private Counted() {
    }

    public static String of(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
