package com.mcpscanner.core.oauth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

final class TokenEndpointRequestFactory {
    void validate(RegisteredClient client, ClientCredentialsAuthentication auth, java.util.Set<String> methods,
            java.util.Set<String> algorithms, boolean publicAllowed) {
        if (!methods.contains(auth.method())) throw invalid();
        if (auth.method().equals("none")) {
            if (!publicAllowed || client.clientSecret() != null) throw invalid();
        } else if (auth.method().equals("client_secret_basic") || auth.method().equals("client_secret_post")) {
            if (client.clientSecret() == null || client.clientSecret().isBlank()) throw invalid();
        } else if (auth.method().equals("private_key_jwt")) {
            var allowed = java.util.Set.of("RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512", "EdDSA");
            if (auth.signer() == null || !allowed.contains(auth.algorithm()) || !algorithms.contains(auth.algorithm())
                    || auth.keyId() != null && auth.keyId().isBlank()) throw invalid();
        } else throw invalid();
    }

    void validate(RegisteredClient client, String method) {
        if (method == null) throw invalid();
        switch (method) {
            case "none" -> { if (client.clientSecret() != null) throw invalid(); }
            case "client_secret_basic", "client_secret_post" -> {
                if (client.clientSecret() == null || client.clientSecret().isBlank()) throw invalid();
            }
            default -> throw invalid();
        }
    }

    private OAuthFlowException invalid() {
        return new OAuthFlowException(OAuthFlowException.Reason.INVALID_CONFIGURATION, "Token authentication method is invalid");
    }

    TokenRequest create(URI endpoint, Map<String, String> grant, RegisteredClient client,
            ClientCredentialsAuthentication authentication, Clock clock) {
        return create(endpoint, grant, client, authentication.method(), authentication, clock);
    }

    TokenRequest create(URI endpoint, Map<String, String> grant, RegisteredClient client, String method, Clock clock) {
        return create(endpoint, grant, client, method, null, clock);
    }

    private TokenRequest create(URI endpoint, Map<String, String> grant, RegisteredClient client, String method,
            ClientCredentialsAuthentication authentication, Clock clock) {
        var form = new LinkedHashMap<>(grant);
        var headers = new LinkedHashMap<>(Map.of("Content-Type", "application/x-www-form-urlencoded", "Accept", "application/json"));
        switch (method) {
            case "none" -> form.put("client_id", client.clientId());
            case "client_secret_basic" -> {
                String pair = URLEncoder.encode(client.clientId(), StandardCharsets.UTF_8) + ":"
                        + URLEncoder.encode(client.clientSecret(), StandardCharsets.UTF_8);
                headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8)));
            }
            case "client_secret_post" -> {
                form.put("client_id", client.clientId());
                form.put("client_secret", client.clientSecret());
            }
            case "private_key_jwt" -> {
                form.put("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
                try { form.put("client_assertion", new ClientAssertionFactory().create(client, endpoint, authentication, clock)); }
                catch (RuntimeException failure) { throw new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED, "Client assertion signing failed"); }
            }
            default -> throw new OAuthFlowException(OAuthFlowException.Reason.INVALID_CONFIGURATION, "Token authentication method is invalid");
        }
        return new TokenRequest(endpoint, form, headers);
    }
}
