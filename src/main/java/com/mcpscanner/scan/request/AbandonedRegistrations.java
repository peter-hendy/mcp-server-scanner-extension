package com.mcpscanner.scan.request;

import burp.api.montoya.http.message.HttpRequestResponse;

@FunctionalInterface
public interface AbandonedRegistrations {

    void abandon(HttpRequestResponse registration, String cause);
}
