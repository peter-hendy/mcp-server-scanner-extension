package com.mcpscanner.auth.oauth;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.http.BareRequests;
import com.mcpscanner.http.BurpMessages;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.HTTPRequestSender;
import com.nimbusds.oauth2.sdk.http.ReadOnlyHTTPRequest;
import com.nimbusds.oauth2.sdk.http.ReadOnlyHTTPResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Nimbus {@link HTTPRequestSender} that routes every OAuth send (metadata fetch,
 * dynamic client registration, token exchange, refresh) through Burp's
 * {@link Http#sendRequest(HttpRequest, RequestOptions) api.http()} so the traffic
 * appears in Burp's proxy history and obeys Burp's networking configuration.
 *
 * <p>Mapping contract:
 * <ul>
 *   <li>Request: method, URL, headers (every value preserved), and body are copied
 *       onto a Montoya {@link HttpRequest}.</li>
 *   <li>Only the Nimbus read timeout maps onto {@link RequestOptions#withResponseTimeout};
 *       Burp owns connection setup, so the connect timeout has no equivalent.</li>
 *   <li>A {@code null} Montoya response (transport failure) is surfaced as an
 *       {@link IOException}, preserving Nimbus's transport-failure contract so existing
 *       {@code catch (IOException)} branches still fire.</li>
 *   <li>Non-2xx responses are returned verbatim (status + headers + body) — never thrown —
 *       so Nimbus's own success/error parsing is unchanged.</li>
 * </ul>
 */
public final class BurpHttpRequestSender implements HTTPRequestSender {

    private final Http http;
    private final BurpMessages messages;
    private final Consumer<HttpRequestResponse> exchangeObserver;

    public BurpHttpRequestSender(Http http) {
        this(http, exchange -> { });
    }

    public BurpHttpRequestSender(Http http, Consumer<HttpRequestResponse> exchangeObserver) {
        this(http, BareRequests::fromUrl, exchangeObserver);
    }

    BurpHttpRequestSender(Http http, Function<String, HttpRequest> requestStarter) {
        this(http, requestStarter, exchange -> { });
    }

    private BurpHttpRequestSender(Http http, Function<String, HttpRequest> requestStarter,
                                  Consumer<HttpRequestResponse> exchangeObserver) {
        this.http = http;
        this.messages = new BurpMessages(requestStarter);
        this.exchangeObserver = exchangeObserver;
    }

    @Override
    public ReadOnlyHTTPResponse send(ReadOnlyHTTPRequest request) throws IOException {
        HttpRequest burpRequest = toBurpRequest(request);
        HttpRequestResponse exchange = http.sendRequest(burpRequest, requestOptions(request));
        if (exchange != null) {
            exchangeObserver.accept(exchange);
        }
        HttpResponse response = exchange != null ? exchange.response() : null;
        if (response == null) {
            throw new IOException("No response from " + request.getURL()
                    + " (Burp api.http() returned a null response)");
        }
        return toNimbusResponse(response);
    }

    private HttpRequest toBurpRequest(ReadOnlyHTTPRequest request) {
        return messages.toRequest(request.getURL().toString(), request.getMethod().name(),
                request.getHeaderMap(), request.getBody());
    }

    private RequestOptions requestOptions(ReadOnlyHTTPRequest request) {
        RequestOptions options = RequestOptions.requestOptions().withRedirectionMode(RedirectionMode.NEVER);
        int readTimeoutMillis = request.getReadTimeout();
        // A 0 / negative read timeout means "no timeout"; leave Burp's default in place.
        if (readTimeoutMillis > 0) {
            options = options.withResponseTimeout(readTimeoutMillis);
        }
        return options;
    }

    private HTTPResponse toNimbusResponse(HttpResponse response) throws IOException {
        HTTPResponse out = new HTTPResponse(response.statusCode());
        for (Map.Entry<String, List<String>> header : BurpMessages.groupedHeaders(response).entrySet()) {
            out.setHeader(header.getKey(), header.getValue().toArray(new String[0]));
        }
        out.setBody(OAuthResponseBodies.read(response));
        return out;
    }
}
