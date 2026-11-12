package com.mcpscanner.ui.detail.schema;

final class OutlineBudget {

    private static final int MAX_ROWS = 500;
    private static final int MAX_REFERENCE_EXPANSIONS = 100;

    private int rows;
    private int referenceExpansions;
    private boolean exceeded;

    boolean admitsRow() {
        if (spend(rows < MAX_ROWS)) {
            rows++;
        }
        return !exceeded;
    }

    boolean admitsExpansion(ResolvedSchema resolved) {
        if (resolved.isReference() && spend(referenceExpansions < MAX_REFERENCE_EXPANSIONS)) {
            referenceExpansions++;
        }
        return !exceeded;
    }

    boolean exceeded() {
        return exceeded;
    }

    private boolean spend(boolean available) {
        exceeded |= !available;
        return !exceeded;
    }
}
