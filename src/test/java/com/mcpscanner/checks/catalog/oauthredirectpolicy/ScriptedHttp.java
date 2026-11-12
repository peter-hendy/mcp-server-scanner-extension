package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ScriptedHttp {

    private final List<Route> routes = new ArrayList<>();
    private final List<HttpRequest> sent = new ArrayList<>();
    private final Http http = mock(Http.class);

    ScriptedHttp() {
        when(http.sendRequest(any(HttpRequest.class))).thenAnswer(invocation -> respond(invocation.getArgument(0)));
    }

    ScriptedHttp on(String method, String url, String rawResponse) {
        return on(method, url, request -> rawResponse);
    }

    ScriptedHttp on(String method, URI url, Function<HttpRequest, String> responder) {
        return on(method, url.toString(), responder);
    }

    ScriptedHttp on(String method, String url, Function<HttpRequest, String> responder) {
        routes.add(new Route(method, url, responder));
        return this;
    }

    Http http() {
        return http;
    }

    OAuthHttp oauth() {
        return new OAuthHttp(http);
    }

    List<HttpRequest> sent() {
        return List.copyOf(sent);
    }

    List<String> sentUrls() {
        return sent.stream().map(HttpRequest::url).toList();
    }

    List<HttpRequest> sent(String method, String url) {
        return sent.stream()
                .filter(request -> new Route(method, url, null).matches(request))
                .toList();
    }

    List<HttpRequest> sent(String method, URI url) {
        return sent(method, url.toString());
    }

    private HttpRequestResponse respond(HttpRequest request) {
        sent.add(request);
        return routes.stream()
                .filter(route -> route.matches(request))
                .findFirst()
                .map(route -> HttpRequestResponse.httpRequestResponse(request,
                        HttpResponse.httpResponse(route.responder().apply(request))))
                .orElse(null);
    }

    private record Route(String method, String url, Function<HttpRequest, String> responder) {

        boolean matches(HttpRequest request) {
            return method.equalsIgnoreCase(request.method())
                    && (request.url().equals(url) || request.url().startsWith(url + "?"));
        }
    }
}
