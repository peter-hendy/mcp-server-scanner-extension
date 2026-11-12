package com.mcpscanner.mcp;

import java.util.List;
import java.util.Set;

final class ReadLikeName {

    private static final Set<String> READ_VERBS = Set.of("get", "list", "search", "read", "fetch", "find",
            "describe", "show", "view", "query", "count", "lookup");

    private ReadLikeName() {
    }

    static boolean matches(String name) {
        List<String> tokens = NameTokens.of(name);
        return !tokens.isEmpty() && readVerbAtAnEnd(tokens) && !WriteLikeName.matches(name);
    }

    private static boolean readVerbAtAnEnd(List<String> tokens) {
        return READ_VERBS.contains(tokens.get(0)) || READ_VERBS.contains(tokens.get(tokens.size() - 1));
    }
}
