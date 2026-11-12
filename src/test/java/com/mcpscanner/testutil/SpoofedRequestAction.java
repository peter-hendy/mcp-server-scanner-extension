package com.mcpscanner.testutil;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.http.handler.RequestAction;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

public record SpoofedRequestAction(RequestAction action, HttpRequest request, HttpResponse response,
                                   Annotations annotations) implements RequestToBeSentAction {
}
