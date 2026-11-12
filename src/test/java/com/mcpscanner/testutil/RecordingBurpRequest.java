package com.mcpscanner.testutil;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class RecordingBurpRequest {

    private final HttpRequest request = mock(HttpRequest.class);
    private final List<String> addedHeaders = new ArrayList<>();
    private String startedUrl;
    private String method;
    private byte[] bodyBytes;

    public RecordingBurpRequest() {
        when(request.withMethod(anyString())).thenAnswer(inv -> {
            method = inv.getArgument(0);
            return request;
        });
        when(request.withAddedHeader(anyString(), anyString())).thenAnswer(inv -> {
            addedHeaders.add(inv.getArgument(0) + ": " + inv.getArgument(1));
            return request;
        });
        when(request.withBody(any(ByteArray.class))).thenAnswer(inv -> {
            bodyBytes = ((ByteArray) inv.getArgument(0)).getBytes();
            return request;
        });
        when(request.withBody(anyString())).thenThrow(
                new AssertionError("Body must be set as UTF-8 bytes, never as a platform-encoded String"));
    }

    public HttpRequest startFrom(String url) {
        startedUrl = url;
        return request;
    }

    public HttpRequest request() {
        return request;
    }

    public String startedUrl() {
        return startedUrl;
    }

    public String method() {
        return method;
    }

    public List<String> addedHeaders() {
        return addedHeaders;
    }

    public byte[] bodyBytes() {
        return bodyBytes;
    }
}
