package com.mcpscanner.ui;

import com.mcpscanner.mcp.CatalogItem;

import java.util.List;
import java.util.Optional;

final class MalformedRows {

    static final String MARK = "⚠ malformed";

    private MalformedRows() {
    }

    static boolean isMalformed(Object item) {
        return item instanceof CatalogItem catalogItem && catalogItem.isMalformed();
    }

    static List<String> problemsOf(Object item) {
        return item instanceof CatalogItem catalogItem ? catalogItem.problems() : List.of();
    }

    static Object mark(Object item, Object cellValue) {
        if (!isMalformed(item)) {
            return cellValue;
        }
        return cellValue == null || cellValue.toString().isEmpty() ? MARK : MARK + " " + cellValue;
    }

    static Optional<String> scanBlocker(Object item, String noun) {
        return isMalformed(item)
                ? Optional.of("This " + noun + " is malformed, so it cannot be scanned")
                : Optional.empty();
    }
}
