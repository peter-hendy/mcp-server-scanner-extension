package com.mcpscanner.mcp;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class NameTokens {

    private NameTokens() {
    }

    public static List<String> of(String name) {
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return Arrays.stream(spaced.split("[^A-Za-z0-9]+"))
                .filter(token -> !token.isEmpty())
                .map(token -> token.toLowerCase(Locale.ROOT))
                .toList();
    }
}
