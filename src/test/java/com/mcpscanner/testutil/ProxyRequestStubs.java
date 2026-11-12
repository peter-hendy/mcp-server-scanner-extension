package com.mcpscanner.testutil;

import fi.iki.elonen.NanoHTTPD;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.lenient;

public final class ProxyRequestStubs {

    private ProxyRequestStubs() {
    }

    public static void stubBody(NanoHTTPD.IHTTPSession request, Map<String, String> headers, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Map<String, String> framed = new HashMap<>(headers);
        if (framed.keySet().stream().noneMatch(ProxyRequestStubs::isFramingHeader)) {
            framed.put("content-length", Integer.toString(bytes.length));
        }
        lenient().when(request.getHeaders()).thenReturn(framed);
        try {
            lenient().when(request.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(bytes));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean isFramingHeader(String name) {
        return name.equalsIgnoreCase("content-length") || name.equalsIgnoreCase("transfer-encoding");
    }
}
