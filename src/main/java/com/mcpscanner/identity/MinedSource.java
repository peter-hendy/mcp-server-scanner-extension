package com.mcpscanner.identity;

import java.util.Objects;

public record MinedSource(String operation, String fieldPath) {

    public MinedSource {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(fieldPath, "fieldPath must not be null");
        operation = withoutQuery(operation);
    }

    private static String withoutQuery(String operation) {
        return operation.replaceFirst("[?#].*$", "");
    }
}
