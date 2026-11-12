package com.mcpscanner.client;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ProtocolHttpRequest(URI uri, String method, Map<String, String> headers, String body) {

    public ProtocolHttpRequest {
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    public boolean hasBody() {
        return body != null;
    }

    public ProtocolHttpRequest redirectedTo(RedirectHop hop) {
        Map<String, String> kept = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            if (hop.keepsHeader(name)) kept.put(name, value);
        });
        return new ProtocolHttpRequest(hop.target(), hop.method(), kept, hop.switchesToGet() ? null : body);
    }
}
