package com.mcpscanner.proxy;

record AuditRequest(AuditProvenance.Status status, ProxyConnection connection) {

    boolean isManaged() {
        return status == AuditProvenance.Status.MANAGED;
    }
}
