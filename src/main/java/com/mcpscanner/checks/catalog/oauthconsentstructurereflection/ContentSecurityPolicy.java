package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class ContentSecurityPolicy {

    private static final List<String> ELEMENT_DIRECTIVES = List.of("script-src-elem", "script-src", "default-src");
    private static final List<String> ATTRIBUTE_DIRECTIVES = List.of("script-src-attr", "script-src", "default-src");
    private static final String BASE_URI = "base-uri";

    private final String text;
    private final Map<String, ScriptSourceList> directives;

    private ContentSecurityPolicy(String text, Map<String, ScriptSourceList> directives) {
        this.text = text;
        this.directives = directives;
    }

    static List<ContentSecurityPolicy> parseAll(List<String> serializedPolicies) {
        return serializedPolicies.stream()
                .flatMap(header -> Arrays.stream(header.split(",")))
                .map(String::trim)
                .filter(policy -> !policy.isEmpty())
                .map(ContentSecurityPolicy::parse)
                .toList();
    }

    private static ContentSecurityPolicy parse(String policy) {
        Map<String, ScriptSourceList> directives = new LinkedHashMap<>();
        for (String directive : policy.split(";")) {
            String[] nameAndValue = directive.trim().split("\\s+", 2);
            String name = nameAndValue[0].toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) {
                directives.putIfAbsent(name, ScriptSourceList.parse(nameAndValue.length > 1 ? nameAndValue[1] : ""));
            }
        }
        return new ContentSecurityPolicy(policy, directives);
    }

    String text() {
        return text;
    }

    ScriptExecution scriptExecution() {
        Optional<ScriptSourceList> elements = effective(ELEMENT_DIRECTIVES);
        Optional<ScriptSourceList> attributes = effective(ATTRIBUTE_DIRECTIVES);
        if (elements.isEmpty() || attributes.isEmpty() || elements.get().allowsInlineScript()
                || attributes.get().allowsInlineScript() || elements.get().allowsArbitraryHostScript()) {
            return ScriptExecution.NOT_BLOCKED;
        }
        if (elements.get().allowsListedHosts() || elements.get().usesNonceOrHash() && !restrictsBaseUri()) {
            return ScriptExecution.UNDETERMINED;
        }
        return ScriptExecution.BLOCKED;
    }

    private boolean restrictsBaseUri() {
        return Optional.ofNullable(directives.get(BASE_URI)).map(ScriptSourceList::onlySameOrigin).orElse(false);
    }

    private Optional<ScriptSourceList> effective(List<String> fallbackChain) {
        return fallbackChain.stream().filter(directives::containsKey).findFirst().map(directives::get);
    }
}
