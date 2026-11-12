package com.mcpscanner.auth.oauth;

import com.nimbusds.oauth2.sdk.http.HTTPRequest;

public final class OAuthHttpTimeouts {

    public static final int CONNECT_MILLIS = 10_000;
    public static final int READ_MILLIS = 10_000;

    private OAuthHttpTimeouts() {
    }

    public static HTTPRequest apply(HTTPRequest request) {
        request.setConnectTimeout(CONNECT_MILLIS);
        request.setReadTimeout(READ_MILLIS);
        return request;
    }
}
