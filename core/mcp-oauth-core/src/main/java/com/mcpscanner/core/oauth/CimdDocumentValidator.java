package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.Map;
import java.util.List;

public final class CimdDocumentValidator {
    public void validate(URI location, Map<String, Object> document, URI callback) {
        validateLocation(location, callback);
        if (document == null || !location.toString().equals(document.get("client_id"))) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_CIMD_DOCUMENT, "Document client_id must exactly match its URL");
        }
        if (!(document.get("client_name") instanceof String name) || name.isBlank()) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_CIMD_DOCUMENT, "Document client_name is required");
        }
        if (!(document.get("redirect_uris") instanceof List<?> redirects)
                || redirects.stream().anyMatch(value -> !(value instanceof String))
                || !redirects.contains(callback.toString())) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_CIMD_DOCUMENT, "Document redirect_uris must contain the exact callback URI");
        }
        if (document.containsKey("token_endpoint_auth_method") && !"none".equals(document.get("token_endpoint_auth_method"))) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_CIMD_DOCUMENT, "CIMD must describe a public client");
        }
    }

    public void validateLocation(URI location, URI callback) {
        if (location == null || !"https".equalsIgnoreCase(location.getScheme()) || location.getHost() == null
                || location.getRawPath() == null || location.getRawPath().isEmpty()
                || location.getRawUserInfo() != null || location.getRawFragment() != null) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_CIMD_URL, "Client metadata URL must use HTTPS and contain a path");
        }
        if (callback == null || !"http".equalsIgnoreCase(callback.getScheme())
                || !("127.0.0.1".equals(callback.getHost()) || "[::1]".equals(callback.getHost()))
                || callback.getPort() < 1 || callback.getPort() > 65535
                || callback.getRawUserInfo() != null || callback.getRawFragment() != null) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_CALLBACK, "CIMD requires a loopback callback with a fixed port");
        }
    }
}
