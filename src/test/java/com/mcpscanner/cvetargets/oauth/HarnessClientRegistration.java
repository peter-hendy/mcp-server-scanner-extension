package com.mcpscanner.cvetargets.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

final class HarnessClientRegistration {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(TIMEOUT).build();

    RegisteredClient register(URI issuer, URI redirectUri, String tokenEndpointAuthMethod, List<String> scopes) {
        JsonNode registered = post(registrationEndpoint(issuer), Map.of(
                "client_name", "mcp-cve-harness",
                "redirect_uris", List.of(redirectUri.toString()),
                "token_endpoint_auth_method", tokenEndpointAuthMethod,
                "grant_types", List.of("authorization_code", "refresh_token"),
                "response_types", List.of("code"),
                "scope", String.join(" ", scopes)));
        return new RegisteredClient(registered.path("client_id").asText(), registered.path("client_secret").asText(null));
    }

    private URI registrationEndpoint(URI issuer) {
        for (URI location : AuthorizationServerLocations.of(issuer)) {
            JsonNode metadata = get(location);
            if (metadata.hasNonNull("registration_endpoint")) {
                return URI.create(metadata.get("registration_endpoint").asText());
            }
        }
        throw new IllegalStateException("no registration_endpoint advertised for " + issuer);
    }

    private JsonNode get(URI location) {
        return send(HttpRequest.newBuilder(location).GET().timeout(TIMEOUT).build(), false);
    }

    private JsonNode post(URI endpoint, Map<String, Object> body) {
        try {
            return send(HttpRequest.newBuilder(endpoint).timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), true);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode send(HttpRequest request, boolean required) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                if (required) {
                    throw new IllegalStateException(request.uri() + " returned " + response.statusCode() + ": "
                            + response.body());
                }
                return JSON.createObjectNode();
            }
            return JSON.readTree(response.body());
        } catch (IOException e) {
            if (required) {
                throw new IllegalStateException(request.uri() + " failed: " + e.getMessage(), e);
            }
            return JSON.createObjectNode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    record RegisteredClient(String clientId, String clientSecret) {
    }
}
