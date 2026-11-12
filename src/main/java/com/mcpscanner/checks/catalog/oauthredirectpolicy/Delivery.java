package com.mcpscanner.checks.catalog.oauthredirectpolicy;

enum Delivery {
    CODE,
    ERROR,
    REJECTED,
    INCONCLUSIVE;

    boolean delivered() {
        return this == CODE || this == ERROR;
    }
}
