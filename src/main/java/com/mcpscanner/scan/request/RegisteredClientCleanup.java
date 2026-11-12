package com.mcpscanner.scan.request;

import burp.api.montoya.http.message.HttpRequestResponse;

@FunctionalInterface
public interface RegisteredClientCleanup {

    void deleteClientRegisteredBy(HttpRequestResponse registration);
}
