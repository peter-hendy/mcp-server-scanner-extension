package com.mcpscanner.client.servercard;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.http.BareRequests;
import com.mcpscanner.mcp.BoundedBodyReader;
import com.mcpscanner.mcp.McpObjectMapper;

import java.net.URI;

public final class ServerCardFetcher {

    private static final long RESPONSE_TIMEOUT_MILLIS = 10_000;
    private static final String NOT_JSON = "not JSON";
    private static final String NO_RESPONSE = "no response";
    private static final String OVERSIZED = "response exceeded " + BoundedBodyReader.MAX_RESPONSE_BYTES + " bytes";

    private final Http http;

    public ServerCardFetcher(Http http) {
        this.http = http;
    }

    public ServerCardReport fetch(URI endpoint) {
        ServerCardUrls urls = ServerCardUrls.forEndpoint(endpoint);
        return new ServerCardReport(fetchDocument(urls.serverCard()), fetchDocument(urls.aiCatalog()));
    }

    private DocumentOutcome fetchDocument(URI url) {
        try {
            return outcomeOf(url, send(url));
        } catch (RuntimeException failure) {
            return new DocumentOutcome.Failed(url, describe(failure));
        }
    }

    private HttpResponse send(URI url) {
        HttpRequest request = BareRequests.fromUrl(url.toString()).withAddedHeader("Accept", "application/json");
        HttpRequestResponse exchange = http.sendRequest(request, requestOptions());
        return exchange == null ? null : exchange.response();
    }

    private static RequestOptions requestOptions() {
        return RequestOptions.requestOptions()
                .withRedirectionMode(RedirectionMode.NEVER)
                .withResponseTimeout(RESPONSE_TIMEOUT_MILLIS);
    }

    private static DocumentOutcome outcomeOf(URI url, HttpResponse response) {
        if (response == null) {
            return new DocumentOutcome.Failed(url, NO_RESPONSE);
        }
        if (response.statusCode() / 100 != 2) {
            return new DocumentOutcome.NotFound(url, response.statusCode());
        }
        if (response.body().length() > BoundedBodyReader.MAX_RESPONSE_BYTES) {
            return new DocumentOutcome.Failed(url, OVERSIZED);
        }
        return parsed(url, response.bodyToString());
    }

    private static DocumentOutcome parsed(URI url, String body) {
        try {
            JsonNode document = McpObjectMapper.RECEIVED_ORDER.readTree(body);
            return document == null || !document.isContainerNode()
                    ? new DocumentOutcome.Failed(url, NOT_JSON)
                    : new DocumentOutcome.Found(url, document);
        } catch (JsonProcessingException notJson) {
            return new DocumentOutcome.Failed(url, NOT_JSON);
        }
    }

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
