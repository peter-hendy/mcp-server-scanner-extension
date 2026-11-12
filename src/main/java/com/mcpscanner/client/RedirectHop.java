package com.mcpscanner.client;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

public record RedirectHop(URI target, String method, boolean switchesToGet) {

    private static final Set<String> BODY_HEADERS = Set.of("content-type", "content-length");

    public boolean keepsHeader(String name) {
        return !switchesToGet || !BODY_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }
}
