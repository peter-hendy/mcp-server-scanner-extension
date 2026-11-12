package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.HostKey;

record FieldSubject(HostKey host, String path, FieldCategory category, String subject) {

    static FieldSubject of(HttpRequest base, ProbedField field) {
        return new FieldSubject(HostKey.of(base.httpService()), base.pathWithoutQuery(), field.category(),
                field.subject());
    }
}
