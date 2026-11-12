package com.mcpscanner.checks.catalog.outboundrequest;

import java.util.Optional;

record ProbedField(FieldCategory category, String field, Optional<String> operation, String request) {

    String subject() {
        return operation.map(this::in).orElse(field);
    }

    String location() {
        return in(request);
    }

    private String in(String where) {
        return field + " in " + where;
    }
}
