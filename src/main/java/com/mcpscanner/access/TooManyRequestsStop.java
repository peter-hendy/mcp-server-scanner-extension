package com.mcpscanner.access;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.TooManyRequestsTrip;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class TooManyRequestsStop extends TooManyRequestsTrip {

    static final String REASON = "the server answered HTTP 429 Too Many Requests";
    private static final String SESSION_DELETE = "DELETE";

    TooManyRequestsStop(Http delegate) {
        super(delegate);
    }

    TooManyRequestsStop(Http delegate, AtomicBoolean tripped) {
        super(delegate, tripped);
    }

    Optional<String> reason() {
        return tripped() ? Optional.of(REASON) : Optional.empty();
    }

    @Override
    protected HttpRequest outgoing(HttpRequest request) {
        if (tripped() && !SESSION_DELETE.equals(request.method())) {
            throw new AccessRunStopped(REASON);
        }
        return request;
    }
}
