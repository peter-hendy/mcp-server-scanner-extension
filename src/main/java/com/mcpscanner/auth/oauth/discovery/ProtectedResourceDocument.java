package com.mcpscanner.auth.oauth.discovery;

import java.net.URI;

public record ProtectedResourceDocument(URI url, String json) {
}
