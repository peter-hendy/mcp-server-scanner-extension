package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.Http;

@FunctionalInterface
public interface BaseRequestSource {
    BaseRequestSet open(ArmEndpoint endpoint, BaseRequestScope scope, Http http);
}
