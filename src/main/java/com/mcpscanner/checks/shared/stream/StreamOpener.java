package com.mcpscanner.checks.shared.stream;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.http.OutsideBurpSends;

@FunctionalInterface
public interface StreamOpener {

    StreamOpening open(HttpRequest request);

    default StreamOpener reportingTo(Http http) {
        return request -> {
            StreamOpening opening = open(request);
            if (opening.sent()) {
                OutsideBurpSends.report(http, opening.exchange());
            }
            return opening;
        };
    }
}
