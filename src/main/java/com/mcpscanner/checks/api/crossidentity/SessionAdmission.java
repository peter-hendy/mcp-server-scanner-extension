package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Optional;
import java.util.Set;

public record SessionAdmission(Optional<HttpRequest> request, OperationReply refusal) {

    public static SessionAdmission admitted(HttpRequest request) {
        return new SessionAdmission(Optional.of(request), OperationReply.none());
    }

    private static final Set<Integer> AUTH_REFUSALS = Set.of(401, 403);

    public static SessionAdmission refused(HttpRequestResponse initialize) {
        boolean authRefusal = initialize != null && AUTH_REFUSALS.contains((int) initialize.response().statusCode());
        return new SessionAdmission(Optional.empty(), authRefusal ? OperationReply.of(initialize) : OperationReply.none());
    }
}
