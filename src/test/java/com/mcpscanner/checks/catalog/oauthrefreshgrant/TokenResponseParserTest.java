package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TokenResponseParserTest {

    private final TokenResponseParser parser = new TokenResponseParser();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void parsesIssuedTokensAndTheDeclaredScope() {
        RefreshExchange exchange = parse(200, "{\"access_token\":\"at\",\"refresh_token\":\"rt\",\"expires_in\":120,"
                + "\"scope\":\"mcp:tools  mcp:admin\"}");

        assertThat(exchange.issued()).isTrue();
        assertThat(exchange.tokens().orElseThrow().accessToken()).isEqualTo("at");
        assertThat(exchange.tokens().orElseThrow().refreshToken()).contains("rt");
        assertThat(exchange.tokens().orElseThrow().lifetime()).isEqualTo(Duration.ofSeconds(120));
        assertThat(exchange.grantedScope().orElseThrow()).containsExactlyInAnyOrder("mcp:tools", "mcp:admin");
    }

    @Test
    void defaultsTheLifetimeAndLeavesAMissingRefreshTokenEmpty() {
        RefreshExchange exchange = parse(200, "{\"access_token\":\"at\"}");

        assertThat(exchange.tokens().orElseThrow().refreshToken()).isEmpty();
        assertThat(exchange.tokens().orElseThrow().lifetime()).isEqualTo(Duration.ofMinutes(5));
        assertThat(exchange.grantedScope()).isEmpty();
    }

    @Test
    void readsTheScopeClaimOfAJwtAccessTokenWhenTheResponseDeclaresNone() {
        RefreshExchange exchange = parse(200, "{\"access_token\":\"" + jwt("{\"scope\":\"a b\"}") + "\"}");

        assertThat(exchange.grantedScope().orElseThrow()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void readsAnScpArrayClaim() {
        RefreshExchange exchange = parse(200, "{\"access_token\":\"" + jwt("{\"scp\":[\"a\",\"c\"]}") + "\"}");

        assertThat(exchange.grantedScope().orElseThrow()).containsExactlyInAnyOrder("a", "c");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"error\":\"invalid_client\"}", "{\"access_token\":\"\"}", "not json", "[]",
            "{\"access_token\":42}"})
    void anythingWithoutATextAccessTokenIsNotIssued(String body) {
        assertThat(parse(200, body).issued()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403})
    void authErrorsAreRefusals(int status) {
        RefreshExchange exchange = parse(status, "{\"error\":\"invalid_client\"}");

        assertThat(exchange.issued()).isFalse();
        assertThat(exchange.refused()).isTrue();
        assertThat(exchange.status()).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 429, 500, 503})
    void otherFailuresAreNeitherIssuedNorRefused(int status) {
        RefreshExchange exchange = parse(status, "{\"access_token\":\"at\"}");

        assertThat(exchange.issued()).isFalse();
        assertThat(exchange.refused()).isFalse();
    }

    @Test
    void aMissingResponseIsNeitherIssuedNorRefused() {
        RefreshExchange exchange = parser.parse(ClientAuthentication.REGISTERED,
                HttpRequestResponse.httpRequestResponse(HttpRequest.httpRequestFromUrl("https://as.example/token"), null));

        assertThat(exchange.issued()).isFalse();
        assertThat(exchange.refused()).isFalse();
        assertThat(exchange.status()).isZero();
    }

    private RefreshExchange parse(int status, String body) {
        HttpResponse response = HttpResponse.httpResponse("HTTP/1.1 " + status + " X\r\nContent-Type: application/json\r\n\r\n" + body);
        return parser.parse(ClientAuthentication.REGISTERED, HttpRequestResponse.httpRequestResponse(
                HttpRequest.httpRequestFromUrl("https://as.example/token"), response));
    }

    private static String jwt(String claims) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }
}
