package com.mcpscanner.identity;

public final class AnonymousComparison {

    public static final String NAME = "Anonymous";

    private AnonymousComparison() {
    }

    public static String alongside(String comparedUsers) {
        return comparedUsers + " + " + NAME;
    }

    public static int comparedIdentities(int comparedUsers) {
        return comparedUsers == 0 ? 0 : comparedUsers + 1;
    }
}
