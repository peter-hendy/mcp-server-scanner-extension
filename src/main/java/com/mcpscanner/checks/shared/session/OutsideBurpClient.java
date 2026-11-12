package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Optional;

interface OutsideBurpClient {

    Optional<HttpRequestResponse> send(HttpRequest request);
}
