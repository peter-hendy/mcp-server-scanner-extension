package com.mcpscanner.checks.catalog.filetraversal;

import java.util.EnumSet;
import java.util.Set;

enum PayloadTier {
    ABSOLUTE,
    TRAVERSAL,
    EVASION,
    IN_ROOT,
    CASE_VARIANT;

    Set<PayloadTier> literalTiers() {
        return switch (this) {
            case EVASION -> EnumSet.of(TRAVERSAL);
            case CASE_VARIANT -> EnumSet.of(IN_ROOT, TRAVERSAL);
            case ABSOLUTE, TRAVERSAL, IN_ROOT -> EnumSet.noneOf(PayloadTier.class);
        };
    }
}
