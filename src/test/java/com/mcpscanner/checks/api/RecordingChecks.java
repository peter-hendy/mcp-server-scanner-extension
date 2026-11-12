package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public final class RecordingChecks {

    private RecordingChecks() {
    }

    public static CheckDescriptor descriptorFor(String id, CheckKind kind) {
        return new CheckDescriptor(id, "Check " + id, "Description of " + id, kind,
                AuditIssueSeverity.MEDIUM, AuditIssueConfidence.FIRM, true, List.of("https://example.test/" + id));
    }

    public record Call(HttpRequestResponse base, AuditInsertionPoint insertionPoint, Http http) {
    }

    public abstract static class Recording {

        private final CheckDescriptor descriptor;
        private final List<AuditIssue> issues;
        public final List<Call> calls = new CopyOnWriteArrayList<>();
        private boolean requiresReply;

        Recording(CheckDescriptor descriptor, List<AuditIssue> issues) {
            this.descriptor = descriptor;
            this.issues = issues;
        }

        public CheckDescriptor descriptor() {
            return descriptor;
        }

        public boolean requiresReply() {
            return requiresReply;
        }

        @SuppressWarnings("unchecked")
        public <T extends Recording> T requiringReply() {
            requiresReply = true;
            return (T) this;
        }

        List<AuditIssue> record(HttpRequestResponse base, AuditInsertionPoint insertionPoint, Http http) {
            calls.add(new Call(base, insertionPoint, http));
            return issues;
        }
    }

    public static final class InsertionPoint extends Recording implements InsertionPointCheck {

        public InsertionPoint(String id, List<AuditIssue> issues) {
            this(descriptorFor(id, CheckKind.INSERTION_POINT), issues);
        }

        public InsertionPoint(CheckDescriptor descriptor, List<AuditIssue> issues) {
            super(descriptor, issues);
        }

        @Override
        public List<AuditIssue> audit(HttpRequestResponse base, AuditInsertionPoint insertionPoint, Http http) {
            return record(base, insertionPoint, http);
        }
    }

    public static final class Request extends Recording implements RequestCheck {

        public Request(String id, List<AuditIssue> issues) {
            this(descriptorFor(id, CheckKind.REQUEST), issues);
        }

        public Request(CheckDescriptor descriptor, List<AuditIssue> issues) {
            super(descriptor, issues);
        }

        @Override
        public List<AuditIssue> audit(HttpRequestResponse base, Http http) {
            return record(base, null, http);
        }
    }

    public static final class Host extends Recording implements HostCheck {

        private final Predicate<HttpRequestResponse> applicable;

        public Host(String id, List<AuditIssue> issues) {
            this(descriptorFor(id, CheckKind.HOST), issues);
        }

        public Host(CheckDescriptor descriptor, List<AuditIssue> issues) {
            this(descriptor, issues, base -> true);
        }

        public Host(String id, List<AuditIssue> issues, Predicate<HttpRequestResponse> applicable) {
            this(descriptorFor(id, CheckKind.HOST), issues, applicable);
        }

        private Host(CheckDescriptor descriptor, List<AuditIssue> issues, Predicate<HttpRequestResponse> applicable) {
            super(descriptor, issues);
            this.applicable = applicable;
        }

        @Override
        public boolean appliesTo(HttpRequestResponse base) {
            return applicable.test(base);
        }

        @Override
        public String appliesOnlyTo() {
            return "recorded requests";
        }

        @Override
        public List<AuditIssue> audit(HttpRequestResponse base, Http http) {
            return record(base, null, http);
        }
    }
}
