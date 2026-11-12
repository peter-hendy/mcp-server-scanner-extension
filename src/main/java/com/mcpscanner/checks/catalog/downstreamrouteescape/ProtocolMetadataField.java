package com.mcpscanner.checks.catalog.downstreamrouteescape;

import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;

import java.util.regex.Pattern;

final class ProtocolMetadataField {

    private static final Pattern META_FIELD = Pattern.compile("^MCP field: params\\._meta\\.");

    private ProtocolMetadataField() {}

    static boolean is(AuditInsertionPoint insertionPoint) {
        String name = insertionPoint.name();
        return name != null && META_FIELD.matcher(name).find();
    }
}
