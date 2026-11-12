package com.mcpscanner.mcp;

import java.util.List;

public interface CatalogItem {

    List<String> problems();

    default boolean isMalformed() {
        return !problems().isEmpty();
    }

    static List<String> requiring(String field, String value) {
        return value == null ? List.of("Missing required field \"" + field + "\"") : List.of();
    }

    static <T extends CatalogItem> List<T> wellFormed(List<T> items) {
        return items == null ? List.of() : items.stream().filter(item -> !item.isMalformed()).toList();
    }
}
