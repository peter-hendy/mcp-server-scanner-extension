package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

record IdField(String name, FieldRole role, Slice slice) {

    int width() {
        return slice.width();
    }
}
