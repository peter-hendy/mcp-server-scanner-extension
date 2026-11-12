package com.mcpscanner.scan.field;

import com.mcpscanner.scan.UriTemplateExpansion;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TemplateExpansionRegistry {

    private final Map<String, Set<UriTemplateExpansion.Variable>> variablesByExpandedUri = new ConcurrentHashMap<>();

    public void record(UriTemplateExpansion.Result expansion) {
        if (!expansion.variables().isEmpty()) {
            variablesByExpandedUri.computeIfAbsent(expansion.expandedUri(), uri -> ConcurrentHashMap.newKeySet())
                    .addAll(expansion.variables());
        }
    }

    public List<UriTemplateExpansion.Variable> variablesIn(String expandedUri) {
        return expandedUri == null ? List.of() : variablesByExpandedUri.getOrDefault(expandedUri, Set.of()).stream()
                .sorted(Comparator.comparingInt(UriTemplateExpansion.Variable::startInclusive)
                        .thenComparingInt(UriTemplateExpansion.Variable::endExclusive))
                .toList();
    }
}
