package com.mcpscanner.engine;

import burp.api.montoya.http.message.requests.HttpRequest;

@FunctionalInterface
public interface RequestNames {

    String nameOf(HttpRequest request);
}
