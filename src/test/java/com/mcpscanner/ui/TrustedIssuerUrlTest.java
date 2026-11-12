package com.mcpscanner.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedIssuerUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://issuer.example",
            " https://issuer.example/tenant ",
            "http://localhost:8080",
            "http://127.0.0.1",
            "http://[::1]:9000"})
    void acceptsHttpsAndLoopbackHttp(String text) {
        assertThat(TrustedIssuerUrl.parse(text)).contains(URI.create(text.trim()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "issuer.example",
            "http://issuer.example",
            "ftp://issuer.example",
            "https://user@issuer.example",
            "https://issuer.example?tenant=a",
            "https://issuer.example#frag",
            "https://bad host"})
    void rejectsAnythingElse(String text) {
        assertThat(TrustedIssuerUrl.parse(text)).isEmpty();
    }
}
