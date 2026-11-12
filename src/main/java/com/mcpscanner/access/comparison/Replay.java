package com.mcpscanner.access.comparison;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.crossidentity.OperationReply;
import com.mcpscanner.checks.api.crossidentity.SessionAdmission;

import java.util.Optional;
import java.util.function.Function;

record Replay(Optional<HttpRequest> request, OperationReply reply) {

    static Replay of(SessionAdmission admission, Function<HttpRequest, OperationReply> send) {
        return new Replay(admission.request(), admission.request().map(send).orElse(admission.refusal()));
    }
}
