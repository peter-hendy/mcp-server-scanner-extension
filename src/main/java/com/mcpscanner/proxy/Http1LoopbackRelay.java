package com.mcpscanner.proxy;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.http.ConnectionClose;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class Http1LoopbackRelay {

    private static final String HTTP_2 = "HTTP/2";
    private static final String CRLF = "\r\n";

    private final Http http;

    public Http1LoopbackRelay(Http http) {
        this.http = Objects.requireNonNull(http);
    }

    public boolean needsRelay(HttpRequestToBeSent request) {
        return isHttp2(request) || !request.toolSource().isFromTool(ToolType.SCANNER);
    }

    public Optional<HttpResponse> relay(HttpRequest request, HttpService loopback) {
        HttpRequest http1 = HttpRequest.httpRequest(loopback, http1Head(request)).withBody(request.body());
        return Optional.ofNullable(http.sendRequest(http1, HttpMode.HTTP_1).response());
    }

    private static boolean isHttp2(HttpRequest request) {
        String version = request.httpVersion();
        return version != null && version.startsWith(HTTP_2);
    }

    private static String http1Head(HttpRequest request) {
        return request.method() + " " + request.path() + " HTTP/1.1" + CRLF
                + request.headers().stream()
                        .filter(Http1LoopbackRelay::isCarriedOver)
                        .map(header -> header.name() + ": " + header.value() + CRLF)
                        .collect(Collectors.joining())
                + ConnectionClose.HEADER + ": " + ConnectionClose.VALUE + CRLF
                + CRLF;
    }

    private static boolean isCarriedOver(HttpHeader header) {
        return !header.name().startsWith(":") && !header.name().equalsIgnoreCase("Content-Length")
                && !ConnectionClose.isConnectionHeader(header.name());
    }
}
