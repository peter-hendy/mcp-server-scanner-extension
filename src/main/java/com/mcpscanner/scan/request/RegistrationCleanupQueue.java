package com.mcpscanner.scan.request;

import burp.api.montoya.http.message.HttpRequestResponse;

@FunctionalInterface
public interface RegistrationCleanupQueue {

    void enqueue(HttpRequestResponse registration, RegisteredClientCleanup cleanup);
}
