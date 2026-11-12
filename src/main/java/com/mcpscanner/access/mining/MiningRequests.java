package com.mcpscanner.access.mining;

import burp.api.montoya.http.message.requests.HttpRequest;

@FunctionalInterface
public interface MiningRequests {

    HttpRequest render(MiningCall call);
}
