package com.mcpscanner.access;

import java.util.function.Predicate;

public enum AccessFilter implements Predicate<AccessRow> {
    ALL("All items") {
        @Override
        public boolean test(AccessRow row) {
            return true;
        }
    },
    DIFFERENCES_ONLY("Differences only") {
        @Override
        public boolean test(AccessRow row) {
            return row.differs();
        }
    };

    private final String label;

    AccessFilter(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
