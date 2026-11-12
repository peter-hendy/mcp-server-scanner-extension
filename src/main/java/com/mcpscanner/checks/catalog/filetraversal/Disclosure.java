package com.mcpscanner.checks.catalog.filetraversal;

enum Disclosure {
    NONE,
    PARTIAL,
    FULL;

    Disclosure strongest(Disclosure other) {
        return compareTo(other) >= 0 ? this : other;
    }
}
