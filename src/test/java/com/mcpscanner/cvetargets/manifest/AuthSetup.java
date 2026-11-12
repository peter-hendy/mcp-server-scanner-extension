package com.mcpscanner.cvetargets.manifest;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;

import java.util.LinkedHashMap;
import java.util.Map;

public record AuthSetup(AuthScheme scheme, String obtain, TokenSource token, String header,
                        Map<String, String> headers) {

    private static final String AUTHORIZATION = "Authorization";

    public AuthSetup {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public AuthSetup(AuthScheme scheme, String obtain, TokenSource token) {
        this(scheme, obtain, token, null, Map.of());
    }

    public AuthSetup(AuthScheme scheme, String obtain, TokenSource token, String header) {
        this(scheme, obtain, token, header, Map.of());
    }

    public AuthSetup(AuthScheme scheme, String obtain, TokenSource token, Map<String, String> headers) {
        this(scheme, obtain, token, null, headers);
    }

    public static AuthSetup none() {
        return new AuthSetup(AuthScheme.NONE, null, null);
    }

    public boolean needsToken() {
        return scheme != AuthScheme.NONE;
    }

    public boolean needsHeaderName() {
        return scheme == AuthScheme.HEADER;
    }

    public AuthStrategy strategy() {
        AuthStrategy credential = credential();
        return headers.isEmpty() ? credential : new HeaderAddingStrategy(credential, headers);
    }

    public AuthSetup including(Map<String, String> armHeaders) {
        Map<String, String> combined = new LinkedHashMap<>(headers);
        combined.putAll(armHeaders);
        return new AuthSetup(scheme, obtain, token, header, combined);
    }

    static boolean namesAuthorization(Map<String, String> headers) {
        return headers.keySet().stream().anyMatch(AUTHORIZATION::equalsIgnoreCase);
    }

    private AuthStrategy credential() {
        if (!needsToken()) {
            return new NoAuthStrategy();
        }
        return needsHeaderName() ? new CustomHeaderAuthStrategy(Map.of(header, token.token())) : token.strategy();
    }
}
