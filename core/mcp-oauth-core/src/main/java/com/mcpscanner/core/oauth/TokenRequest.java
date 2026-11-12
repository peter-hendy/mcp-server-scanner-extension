package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.Map;

public record TokenRequest(URI endpoint, Map<String, String> form, Map<String, String> headers) {
    public TokenRequest { form = Map.copyOf(form); headers = Map.copyOf(headers); }
    public String method() { return "POST"; }
    public String body() { return FormEncoding.encode(form); }
    @Override public String toString() { return "TokenRequest[redacted]"; }
}
