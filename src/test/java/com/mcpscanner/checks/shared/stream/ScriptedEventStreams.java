package com.mcpscanner.checks.shared.stream;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.checks.shared.probe.ProbeChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class ScriptedEventStreams implements EventStreams {

    public static final String PROBE_SESSION = "probe-session";

    public final List<HttpRequest> opened = Collections.synchronizedList(new ArrayList<>());
    private final Function<HttpRequest, StreamOpening> answer;
    private final Optional<String> sessionId;

    private ScriptedEventStreams(Function<HttpRequest, StreamOpening> answer, Optional<String> sessionId) {
        this.answer = answer;
        this.sessionId = sessionId;
    }

    private static Function<HttpRequest, StreamOpening> openingWhen(Predicate<HttpRequest> opens) {
        return request -> opens.test(request) ? held(request) : refused(request);
    }

    public static ScriptedEventStreams inSessions(Predicate<HttpRequest> opens) {
        return new ScriptedEventStreams(openingWhen(opens), Optional.of(PROBE_SESSION));
    }

    public static ScriptedEventStreams stateless(Predicate<HttpRequest> opens) {
        return new ScriptedEventStreams(openingWhen(opens), Optional.empty());
    }

    public static ScriptedEventStreams unanswered() {
        return new ScriptedEventStreams(StreamOpening::unanswered, Optional.of(PROBE_SESSION));
    }

    public static ScriptedEventStreams answeringWith(int status, boolean heldOpen) {
        return new ScriptedEventStreams(request -> new StreamOpening(HttpRequestResponse.httpRequestResponse(request,
                HttpResponse.httpResponse("HTTP/1.1 " + status + "\r\nContent-Type: text/event-stream\r\n\r\n")),
                heldOpen), Optional.of(PROBE_SESSION));
    }

    public static EventStreams none() {
        return (base, http, budget, shaping) -> Optional.empty();
    }

    @Override
    public Optional<StreamOpening> open(HttpRequestResponse base, Http http, ProbeChannel budget,
                                        UnaryOperator<HttpRequest> shaping) {
        HttpRequest request = shaping.apply(StreamGetRequest.from(base.request(), sessionId));
        opened.add(request);
        return Optional.of(answer.apply(request));
    }

    private static StreamOpening held(HttpRequest request) {
        return new StreamOpening(HttpRequestResponse.httpRequestResponse(request, HttpResponse.httpResponse(
                "HTTP/1.1 200\r\nContent-Type: text/event-stream\r\n\r\n")), true);
    }

    private static StreamOpening refused(HttpRequest request) {
        return new StreamOpening(HttpRequestResponse.httpRequestResponse(request, HttpResponse.httpResponse(
                "HTTP/1.1 401\r\nContent-Type: text/plain\r\n\r\nUnauthorized")), false);
    }
}
