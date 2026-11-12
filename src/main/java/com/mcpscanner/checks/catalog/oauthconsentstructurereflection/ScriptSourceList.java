package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import java.util.List;
import java.util.Locale;
import java.util.Set;

record ScriptSourceList(List<String> sources) {

    private static final Set<String> SCHEME_SOURCES = Set.of("http:", "https:", "data:", "blob:");
    private static final Set<String> SAME_ORIGIN_SOURCES = Set.of("'self'", "'none'");

    static ScriptSourceList parse(String value) {
        return new ScriptSourceList(List.of(value.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .stream().filter(source -> !source.isEmpty()).toList());
    }

    boolean allowsInlineScript() {
        return sources.contains("'unsafe-inline'") && !usesNonceOrHash() && !isStrictDynamic();
    }

    boolean allowsArbitraryHostScript() {
        return !isStrictDynamic() && sources.stream()
                .anyMatch(source -> SCHEME_SOURCES.contains(source) || source.contains("*"));
    }

    boolean allowsListedHosts() {
        return !isStrictDynamic() && sources.stream().anyMatch(source -> !source.startsWith("'"));
    }

    boolean usesNonceOrHash() {
        return sources.stream().anyMatch(source -> source.startsWith("'nonce-") || source.startsWith("'sha"));
    }

    boolean onlySameOrigin() {
        return !sources.isEmpty() && SAME_ORIGIN_SOURCES.containsAll(sources);
    }

    private boolean isStrictDynamic() {
        return sources.contains("'strict-dynamic'");
    }
}
