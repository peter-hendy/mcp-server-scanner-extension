package com.mcpscanner.access;

import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.SendOnlyBurpHttp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RecordingHttp extends SendOnlyBurpHttp {
    final List<HttpRequest> sent = new CopyOnWriteArrayList<>();
    private volatile String failingBody;
    private volatile String everyRequestFailure;
    private volatile String rateLimitedBody;
    private volatile boolean rateLimitingEveryRequest;
    private volatile String firstRequestRetryAfter;
    private final AtomicBoolean firstRequestAnswered = new AtomicBoolean();
    private volatile String retryAfterBody;
    private volatile String retryAfterForBody;

    public void failFor(String body) {
        failingBody = body;
    }

    public void rateLimit(String body) {
        rateLimitedBody = body;
    }

    public void rateLimitEveryRequest() {
        rateLimitingEveryRequest = true;
    }

    public void rateLimitFirstRequestAsking(String retryAfter) {
        firstRequestRetryAfter = retryAfter;
    }

    public void rateLimitAsking(String body, String retryAfter) {
        retryAfterBody = body;
        retryAfterForBody = retryAfter;
    }

    public void failEveryRequestWith(String reason) {
        everyRequestFailure = reason;
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request) {
        return sendRequest(request, RequestOptions.requestOptions());
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions options) {
        sent.add(request);
        if (everyRequestFailure != null) {
            throw new IllegalStateException(everyRequestFailure);
        }
        if (request.bodyToString().equals(failingBody)) {
            throw new IllegalStateException("connection refused");
        }
        if (firstRequestRetryAfter != null && firstRequestAnswered.compareAndSet(false, true)) {
            return HttpRequestResponse.httpRequestResponse(request,
                    rawResponse(429, Map.of("Retry-After", List.of(firstRequestRetryAfter)), new byte[0]));
        }
        if (request.bodyToString().equals(retryAfterBody)) {
            return HttpRequestResponse.httpRequestResponse(request,
                    rawResponse(429, Map.of("Retry-After", List.of(retryAfterForBody)), new byte[0]));
        }
        boolean rateLimited = rateLimitingEveryRequest || request.bodyToString().equals(rateLimitedBody);
        int status = rateLimited ? 429 : 200;
        return HttpRequestResponse.httpRequestResponse(request, rawResponse(status, Map.of(), "{}".getBytes()));
    }
}
