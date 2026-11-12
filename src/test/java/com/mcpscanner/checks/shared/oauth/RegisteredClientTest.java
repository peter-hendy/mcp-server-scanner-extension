package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.HttpRequestResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RegisteredClientTest {

    private final HttpRequestResponse evidence = mock(HttpRequestResponse.class);

    @Test
    void listsAnEchoedUriIgnoringCase() {
        assertThat(client(List.of("JavaScript:void(0)")).lists("javascript:void(0)")).isTrue();
    }

    @Test
    void doesNotListAnUnechoedUri() {
        assertThat(client(List.of("https://client.example.com/a")).lists("https://client.example.com/b")).isFalse();
    }

    @Test
    void keepsItsOwnCopyOfTheRedirectUris() {
        List<String> redirectUris = new ArrayList<>(List.of("https://client.example.com/a"));
        RegisteredClient client = client(redirectUris);

        redirectUris.add("https://client.example.com/b");

        assertThat(client.redirectUris()).containsExactly("https://client.example.com/a");
    }

    private RegisteredClient client(List<String> redirectUris) {
        return new RegisteredClient("c1", redirectUris, evidence, Optional.empty());
    }
}
