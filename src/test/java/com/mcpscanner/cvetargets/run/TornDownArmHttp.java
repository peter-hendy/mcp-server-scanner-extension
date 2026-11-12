package com.mcpscanner.cvetargets.run;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.cvetargets.requests.SendOnlyHttp;

public final class TornDownArmHttp extends SendOnlyHttp {

    private final Http delegate;
    private final ArmTeardown teardown;

    public TornDownArmHttp(Http delegate, ArmTeardown teardown) {
        this.delegate = delegate;
        this.teardown = teardown;
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request) {
        if (teardown.isClosed()) {
            throw new IllegalStateException("the arm was torn down at its deadline; probe not sent");
        }
        return delegate.sendRequest(request);
    }
}
