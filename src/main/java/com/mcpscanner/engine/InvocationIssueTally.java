package com.mcpscanner.engine;

import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class InvocationIssueTally {

    private final ThreadLocal<List<AuditIssue>> addedDuringInvocation = new ThreadLocal<>();

    List<AuditIssue> collecting(Supplier<List<AuditIssue>> invocation) {
        List<AuditIssue> added = new ArrayList<>();
        addedDuringInvocation.set(added);
        try {
            List<AuditIssue> reported = new ArrayList<>(invocation.get());
            reported.addAll(added);
            return List.copyOf(reported);
        } finally {
            addedDuringInvocation.remove();
        }
    }

    boolean recorded(List<AuditIssue> issues) {
        List<AuditIssue> added = addedDuringInvocation.get();
        if (added == null) {
            return false;
        }
        added.addAll(issues);
        return true;
    }
}
