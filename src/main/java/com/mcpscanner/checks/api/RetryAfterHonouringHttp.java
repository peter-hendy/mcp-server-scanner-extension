package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.http.ForwardingHttp;
import com.mcpscanner.mcp.RetryAfter;
import com.mcpscanner.mcp.TooManyRequests;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Function;

public final class RetryAfterHonouringHttp extends ForwardingHttp {

    public static final Duration MAX_WAIT = Duration.ofMinutes(5);

    private final CheckLog log;
    private final Predicate<String> firstNotice;
    private final RetryAfterWaits waits;
    private final RetryWait retryWait;
    private final Clock clock;

    public RetryAfterHonouringHttp(Http delegate, CheckLog log, Predicate<String> firstNotice, RetryAfterWaits waits) {
        this(delegate, log, firstNotice, waits, new SlicedWait(Sleeper.THREAD), Clock.systemUTC());
    }

    RetryAfterHonouringHttp(Http delegate, CheckLog log, Predicate<String> firstNotice, RetryAfterWaits waits,
                            RetryWait retryWait, Clock clock) {
        super(delegate);
        this.log = log;
        this.firstNotice = firstNotice;
        this.waits = waits;
        this.retryWait = retryWait;
        this.clock = clock;
    }

    @Override
    protected HttpRequest outgoing(HttpRequest request) {
        return request;
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request) {
        return honouring(request, super::sendRequest);
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode) {
        return honouring(request, sent -> super.sendRequest(sent, httpMode));
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode, String connectionId) {
        return honouring(request, sent -> super.sendRequest(sent, httpMode, connectionId));
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions requestOptions) {
        return honouring(request, sent -> super.sendRequest(sent, requestOptions));
    }

    @Override
    public List<HttpRequestResponse> sendRequests(List<HttpRequest> requests) {
        return requests.stream().map(this::sendRequest).toList();
    }

    @Override
    public List<HttpRequestResponse> sendRequests(List<HttpRequest> requests, HttpMode httpMode) {
        return requests.stream().map(request -> sendRequest(request, httpMode)).toList();
    }

    private HttpRequestResponse honouring(HttpRequest request, Function<HttpRequest, HttpRequestResponse> send) {
        HttpRequestResponse first = send.apply(request);
        if (!TooManyRequests.answered(first)) {
            return first;
        }
        String host = hostOf(request);
        if (!waits.onWaitingThread()) {
            notWaiting(host, "not waiting on a concurrent probe");
            return first;
        }
        return honourableWait(first.response(), host)
                .filter(wait -> waited(wait, host))
                .map(wait -> send.apply(request))
                .orElse(first);
    }

    private Optional<Duration> honourableWait(HttpResponse rateLimited, String host) {
        Optional<Duration> asked = RetryAfter.of(rateLimited, clock);
        if (asked.isEmpty()) {
            notWaiting(host, "no Retry-After; not waiting");
            return Optional.empty();
        }
        long seconds = asked.get().toSeconds();
        if (asked.get().compareTo(MAX_WAIT) > 0) {
            notWaiting(host, "Retry-After " + seconds + " s is over 5 min; not waiting");
            return Optional.empty();
        }
        note(host, " (waiting)", "waiting " + seconds + " s as Retry-After asks, then resending");
        return asked;
    }

    private boolean waited(Duration wait, String host) {
        try {
            if (retryWait.waitedOut(wait, waits::stillWanted)) {
                return true;
            }
            log.debug(notice(host) + ": wait abandoned (no longer wanted); returning the 429 without resending");
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void notWaiting(String host, String outcome) {
        note(host, " (not waiting)", outcome);
    }

    private void note(String host, String waiting, String outcome) {
        String notice = notice(host);
        log.firstAtInfo(firstNotice, notice + waiting, notice + ": " + outcome);
    }

    private static String notice(String host) {
        return "HTTP 429 from " + host;
    }

    private static String hostOf(HttpRequest request) {
        return request.httpService() == null ? request.url() : request.httpService().host();
    }
}
