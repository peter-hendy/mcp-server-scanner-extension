package com.mcpscanner.integration;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class IdentityToolCalls {

    private IdentityToolCalls() {
    }

    static HttpRequestResponse base(Http http, ScannerProxyFixture proxy, String tool) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(proxy.session().resolvedEndpoint()).withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "application/json, text/event-stream");
        for (Map.Entry<String, String> header : proxy.baseHeaders().entrySet()) {
            request = request.withHeader(header.getKey(), header.getValue());
        }
        HttpRequestResponse base = http.sendRequest(request.withBody("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":"
                + "\"tools/call\",\"params\":{\"name\":\"" + tool + "\",\"arguments\":{}}}"));
        assertThat(base.response().statusCode()).as("base request").isEqualTo((short) 200);
        return base;
    }
}
