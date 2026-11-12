package com.mcpscanner.testutil;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Registration;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.execution.RequestEngineOptions;
import burp.api.montoya.http.execution.RequestExecutionEngine;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.http.message.responses.analysis.ResponseKeywordsAnalyzer;
import burp.api.montoya.http.message.responses.analysis.ResponseVariationsAnalyzer;
import burp.api.montoya.http.sessions.CookieJar;
import burp.api.montoya.http.sessions.SessionHandlingAction;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public abstract class SendOnlyBurpHttp implements Http {

    @Override
    public abstract HttpRequestResponse sendRequest(HttpRequest request, RequestOptions requestOptions);

    protected static HttpResponse rawResponse(int status, Map<String, List<String>> headers, byte[] body) {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(" \r\n");
        headers.forEach((name, values) ->
                values.forEach(value -> head.append(name).append(": ").append(value).append("\r\n")));
        head.append("\r\n");
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.writeBytes(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        raw.writeBytes(body);
        return HttpResponse.httpResponse(ByteArray.byteArray(raw.toByteArray()));
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request) {
        throw unsupported();
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode) {
        throw unsupported();
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode, String connectionId) {
        throw unsupported();
    }

    @Override
    public List<HttpRequestResponse> sendRequests(List<HttpRequest> requests) {
        throw unsupported();
    }

    @Override
    public List<HttpRequestResponse> sendRequests(List<HttpRequest> requests, HttpMode httpMode) {
        throw unsupported();
    }

    @Override
    public Registration registerHttpHandler(HttpHandler handler) {
        throw unsupported();
    }

    @Override
    public Registration registerSessionHandlingAction(SessionHandlingAction sessionHandlingAction) {
        throw unsupported();
    }

    @Override
    public ResponseKeywordsAnalyzer createResponseKeywordsAnalyzer(List<String> keywords) {
        throw unsupported();
    }

    @Override
    public ResponseVariationsAnalyzer createResponseVariationsAnalyzer() {
        throw unsupported();
    }

    @Override
    public RequestExecutionEngine createRequestEngine() {
        throw unsupported();
    }

    @Override
    public RequestExecutionEngine createRequestEngine(RequestEngineOptions options) {
        throw unsupported();
    }

    @Override
    public CookieJar cookieJar() {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Only sendRequest(HttpRequest, RequestOptions) is supported");
    }
}
