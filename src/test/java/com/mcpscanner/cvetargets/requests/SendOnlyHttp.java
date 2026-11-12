package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.core.Registration;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.execution.RequestEngineOptions;
import burp.api.montoya.http.execution.RequestExecutionEngine;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.analysis.ResponseKeywordsAnalyzer;
import burp.api.montoya.http.message.responses.analysis.ResponseVariationsAnalyzer;
import burp.api.montoya.http.sessions.CookieJar;
import burp.api.montoya.http.sessions.SessionHandlingAction;

import java.util.List;

public abstract class SendOnlyHttp implements Http {

    @Override
    public abstract HttpRequestResponse sendRequest(HttpRequest request);

    @Override
    public final HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode) {
        return sendRequest(request);
    }

    @Override
    public final HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode, String connectionId) {
        return sendRequest(request);
    }

    @Override
    public final HttpRequestResponse sendRequest(HttpRequest request, RequestOptions requestOptions) {
        return sendRequest(request);
    }

    @Override
    public final List<HttpRequestResponse> sendRequests(List<HttpRequest> requests) {
        return requests.stream().map(this::sendRequest).toList();
    }

    @Override
    public final List<HttpRequestResponse> sendRequests(List<HttpRequest> requests, HttpMode httpMode) {
        return sendRequests(requests);
    }

    @Override
    public final RequestExecutionEngine createRequestEngine() {
        throw unsupported("createRequestEngine");
    }

    @Override
    public final RequestExecutionEngine createRequestEngine(RequestEngineOptions options) {
        throw unsupported("createRequestEngine");
    }

    @Override
    public final Registration registerHttpHandler(HttpHandler handler) {
        throw unsupported("registerHttpHandler");
    }

    @Override
    public final Registration registerSessionHandlingAction(SessionHandlingAction sessionHandlingAction) {
        throw unsupported("registerSessionHandlingAction");
    }

    @Override
    public final ResponseKeywordsAnalyzer createResponseKeywordsAnalyzer(List<String> keywords) {
        throw unsupported("createResponseKeywordsAnalyzer");
    }

    @Override
    public final ResponseVariationsAnalyzer createResponseVariationsAnalyzer() {
        throw unsupported("createResponseVariationsAnalyzer");
    }

    @Override
    public final CookieJar cookieJar() {
        throw unsupported("cookieJar");
    }

    private UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException(operation + " is not supported by " + getClass().getSimpleName());
    }
}
