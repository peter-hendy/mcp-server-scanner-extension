package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;
import java.util.stream.Stream;

public record Separation(OperationReply baseline, OperationReply candidate, OperationReply control) {

    public List<HttpRequestResponse> evidence(OperationReply owner) {
        return Stream.of(owner, baseline, candidate, control).map(OperationReply::exchange).toList();
    }
}
