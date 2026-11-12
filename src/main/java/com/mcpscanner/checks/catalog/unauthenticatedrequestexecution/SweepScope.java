package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.HostKey;

record SweepScope(HostKey host, String path, String method) {

    private static final String EVENT_STREAM = "GET event stream";

    static SweepScope of(AuditSubject subject) {
        HttpRequest seed = subject.seed();
        return new SweepScope(HostKey.of(seed.httpService()), seed.path(), subject.message().method());
    }

    static SweepScope eventStreamOf(AuditSubject subject) {
        HttpRequest seed = subject.seed();
        return new SweepScope(HostKey.of(seed.httpService()), seed.pathWithoutQuery(), EVENT_STREAM);
    }
}
