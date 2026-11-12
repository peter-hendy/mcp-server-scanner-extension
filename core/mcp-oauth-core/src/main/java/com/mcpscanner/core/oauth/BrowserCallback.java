package com.mcpscanner.core.oauth;

import java.net.URI;

public record BrowserCallback(URI callbackUri, AuthorizationCallback parameters) {
    @Override public String toString() { return "BrowserCallback[redacted]"; }
}
