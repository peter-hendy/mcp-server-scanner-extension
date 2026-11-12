package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ScriptedHttp {

    public static final String NO_RESPONSE = null;
    private static final String NOT_FOUND = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\n\r\nnot found";

    private final Map<String, Function<HttpRequest, String>> routes = new HashMap<>();
    private final List<HttpRequest> sent = Collections.synchronizedList(new ArrayList<>());

    public ScriptedHttp on(String method, String urlWithoutQuery, String rawResponse) {
        return on(method, urlWithoutQuery, request -> rawResponse);
    }

    public ScriptedHttp on(String method, String urlWithoutQuery, Function<HttpRequest, String> responder) {
        routes.put(method + " " + urlWithoutQuery, responder);
        return this;
    }

    public Function<HttpRequest, String> responderFor(String method, String urlWithoutQuery) {
        return routes.get(method + " " + urlWithoutQuery);
    }

    public OAuthHttp oauth() {
        return new OAuthHttp(http());
    }

    public List<String> sentUrls() {
        return sent.stream().map(HttpRequest::url).toList();
    }

    public static String status(int status) {
        return "HTTP/1.1 " + status + " X\r\n\r\n";
    }

    public static String withHeader(int status, String name, String value) {
        return "HTTP/1.1 " + status + " X\r\n" + name + ": " + value + "\r\n\r\n";
    }

    public Http http() {
        Http http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class))).thenAnswer(invocation -> respond(invocation.getArgument(0)));
        return http;
    }

    public List<HttpRequest> sent() {
        return List.copyOf(sent);
    }

    public List<HttpRequest> sentTo(String urlWithoutQuery) {
        return sent.stream().filter(request -> withoutQuery(request.url()).equals(urlWithoutQuery)).toList();
    }

    public static String json(int status, String body) {
        return "HTTP/1.1 " + status + " X\r\nContent-Type: application/json\r\n\r\n" + body;
    }

    public static String html(String body, String... headers) {
        return html(200, body, headers);
    }

    public static String html(int status, String body, String... headers) {
        StringBuilder raw = new StringBuilder("HTTP/1.1 " + status + " X\r\nContent-Type: text/html; charset=utf-8\r\n");
        Arrays.stream(headers).forEach(header -> raw.append(header).append("\r\n"));
        return raw.append("\r\n").append(body).toString();
    }

    public static String redirect(String location) {
        return "HTTP/1.1 302 Found\r\nLocation: " + location + "\r\n\r\n";
    }

    public static String queryParameter(HttpRequest request, String name) {
        String query = URI.create(request.url()).getRawQuery();
        if (query == null) {
            return null;
        }
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name))
                .map(pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "")
                .findFirst()
                .orElse(null);
    }

    private HttpRequestResponse respond(HttpRequest request) {
        sent.add(request);
        Function<HttpRequest, String> responder = routes.get(request.method() + " " + withoutQuery(request.url()));
        String raw = responder == null ? NOT_FOUND : responder.apply(request);
        HttpResponse response = raw == null ? null : HttpResponse.httpResponse(raw);
        return HttpRequestResponse.httpRequestResponse(request, response);
    }

    private static String withoutQuery(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }
}
