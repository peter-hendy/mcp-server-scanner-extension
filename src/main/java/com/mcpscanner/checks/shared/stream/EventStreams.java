package com.mcpscanner.checks.shared.stream;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.probe.ProbeChannel;

import java.util.Optional;
import java.util.function.UnaryOperator;

@FunctionalInterface
public interface EventStreams {

    Optional<StreamOpening> open(HttpRequestResponse base, Http http, ProbeChannel budget,
                                 UnaryOperator<HttpRequest> shaping);
}
