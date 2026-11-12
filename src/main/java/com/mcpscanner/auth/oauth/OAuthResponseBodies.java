package com.mcpscanner.auth.oauth;

import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.mcp.BoundedBodyReader;

import java.io.IOException;

public final class OAuthResponseBodies {

    private OAuthResponseBodies() {
    }

    public static String read(HttpResponse response) throws IOException {
        if (response.body() == null) {
            return null;
        }
        if (response.body().length() > BoundedBodyReader.MAX_RESPONSE_BYTES) {
            throw new IOException("OAuth response exceeded " + BoundedBodyReader.MAX_RESPONSE_BYTES + " bytes");
        }
        return response.bodyToString();
    }
}
