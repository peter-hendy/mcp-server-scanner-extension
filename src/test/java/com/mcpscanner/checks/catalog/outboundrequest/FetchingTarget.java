package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.collaborator.FakeCollaborator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class FetchingTarget {

    private static final Pattern PAYLOAD_HOST = Pattern.compile("([a-z0-9]+)\\." + Pattern.quote(FakeCollaborator.DOMAIN));

    private final FakeCollaborator collaborator;
    private final List<HttpRequest> received = new ArrayList<>();
    private Predicate<HttpRequest> fetches = request -> false;
    private InteractionType interactionType = InteractionType.HTTP;
    private Map<String, String> forwardedHeaders = Map.of();
    private int answeredBeforeRateLimit = Integer.MAX_VALUE;

    FetchingTarget(FakeCollaborator collaborator) {
        this.collaborator = collaborator;
    }

    FetchingTarget fetchingWhen(Predicate<HttpRequest> condition) {
        this.fetches = condition;
        return this;
    }

    FetchingTarget resolvingOnly() {
        this.interactionType = InteractionType.DNS;
        return this;
    }

    FetchingTarget forwarding(Map<String, String> headers) {
        this.forwardedHeaders = headers;
        return this;
    }

    FetchingTarget rateLimiting() {
        return rateLimitingAfter(0);
    }

    FetchingTarget rateLimitingAfter(int answered) {
        this.answeredBeforeRateLimit = answered;
        return this;
    }

    List<HttpRequest> received() {
        return received;
    }

    Http http() {
        Http http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class))).thenAnswer(invocation -> respond(invocation.getArgument(0)));
        return http;
    }

    private HttpRequestResponse respond(HttpRequest request) {
        received.add(request);
        if (received.size() > answeredBeforeRateLimit) {
            return HttpRequestResponse.httpRequestResponse(request, OutboundFixtures.tooManyRequests());
        }
        if (fetches.test(request)) {
            Matcher matcher = PAYLOAD_HOST.matcher(textOf(request));
            while (matcher.find()) {
                interact(FakeCollaborator.payload(matcher.group(1)));
            }
        }
        return OutboundFixtures.exchange(request);
    }

    private void interact(CollaboratorPayload payload) {
        if (interactionType == InteractionType.DNS) {
            collaborator.interact(payload, InteractionType.DNS);
            return;
        }
        Map<String, String> headers = new LinkedHashMap<>(forwardedHeaders);
        headers.put("Host", payload.toString());
        HttpRequest fetch = OutboundFixtures.post("http://" + payload + "/", headers, "");
        collaborator.interactOverHttp(payload, HttpRequestResponse.httpRequestResponse(fetch, null));
    }

    private static String textOf(HttpRequest request) {
        StringBuilder text = new StringBuilder();
        request.headers().forEach(header -> text.append(header.name()).append(": ").append(header.value()).append('\n'));
        return text.append(request.bodyToString()).toString();
    }

    static Predicate<HttpRequest> bodyContains(String fragment) {
        return request -> request.bodyToString().contains(fragment);
    }

    static Predicate<HttpRequest> headerCarriesPayload(String name) {
        return request -> request.headers().stream().anyMatch(header -> header.name().equalsIgnoreCase(name)
                && header.value().contains(FakeCollaborator.DOMAIN));
    }
}
