package com.mcpscanner.testutil;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.responses.HttpResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class BurpEmptyResponse {

    private BurpEmptyResponse() {
    }

    public static HttpResponse create() {
        ByteArray noBytes = ByteArray.byteArray(new byte[0]);
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) 0);
        when(response.toByteArray()).thenReturn(noBytes);
        return response;
    }
}
