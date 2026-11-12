package com.mcpscanner.checks.catalog.jwtvalidation;

import com.mcpscanner.checks.shared.oauth.ScriptedHttp;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.json;
import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.status;
import static org.assertj.core.api.Assertions.assertThat;

class PublishedKeyDiscoveryTest {

    private static final String RSA_JWK = "{\"kty\":\"RSA\",\"kid\":\"k1\",\"e\":\"AQAB\",\"n\":\""
            + "u1SU1LfVLPHCozMxH2Mo4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0_IzW7yWR7QkrmBL7jTKEn5u-qKhbwKfBstIs-bMY2Zk"
            + "p18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyehkd3qqGElvW_VDL5AaWTg0nLVkjRo9z-40RQzuVaE8AkAFmxZzow3x-VJYKdj"
            + "ykkJ0iT9wCS0DRTXu269V264Vf_3jvredZiKRkgwlL9xNAwxXFg0x_XFw005UWVRIkdgcKWTjpBP2dPwVZ4WWC-9aGVd-Gyn1o0CLelf"
            + "4rEjGoXbAAEgAqeGUxrcIlbjXfbcmw\"}";

    private final ScriptedHttp http = new ScriptedHttp();
    private final PublishedKeyDiscovery discovery = new PublishedKeyDiscovery(new JwkPemEncoder());

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private void publish(String url, String document) {
        http.on("GET", url, json(200, document));
    }

    private List<PublicKeyMaterial> discover(String endpoint, String token) {
        return discovery.discover(endpoint, CompactJwt.parse(token).orElseThrow(), http.oauth());
    }

    private List<String> fetched() {
        return http.sentUrls();
    }

    @Test
    void followsProtectedResourceMetadataToTheAuthorizationServerJwks() {
        publish("https://mcp.example/.well-known/oauth-protected-resource/mcp",
                "{\"authorization_servers\":[\"https://as.example/tenant\"]}");
        publish("https://as.example/.well-known/oauth-authorization-server/tenant",
                "{\"jwks_uri\":\"https://as.example/keys\"}");
        publish("https://as.example/keys", "{\"keys\":[" + RSA_JWK + "]}");

        List<PublicKeyMaterial> keys = discover("https://mcp.example/mcp", TestTokens.token("{\"alg\":\"RS256\"}", "{}"));

        assertThat(keys).hasSize(4);
        assertThat(keys.get(0).description()).contains("k1", "https://as.example/keys");
    }

    @Test
    void fallsBackToTheRootProtectedResourceDocumentAndOpenIdConfiguration() {
        publish("https://mcp.example/.well-known/oauth-protected-resource",
                "{\"authorization_servers\":[\"https://as.example/\"]}");
        publish("https://as.example/.well-known/openid-configuration", "{\"jwks_uri\":\"https://as.example/keys\"}");
        publish("https://as.example/keys", "{\"keys\":[" + RSA_JWK + "]}");

        assertThat(discover("https://mcp.example/mcp", TestTokens.token("{\"alg\":\"RS256\"}", "{}"))).hasSize(4);
    }

    @Test
    void usesTheTokenIssuerWhenNoProtectedResourceMetadataExists() {
        publish("https://issuer.example/.well-known/oauth-authorization-server",
                "{\"jwks_uri\":\"https://issuer.example/jwks.json\"}");
        publish("https://issuer.example/jwks.json", "{\"keys\":[" + RSA_JWK + "]}");

        assertThat(discover("https://mcp.example/mcp",
                TestTokens.token("{\"alg\":\"RS256\"}", "{\"iss\":\"https://issuer.example\"}"))).hasSize(4);
    }

    @Test
    void deduplicatesKeysPublishedByTheSameJwksAcrossIssuers() {
        publish("https://mcp.example/.well-known/oauth-protected-resource/mcp",
                "{\"authorization_servers\":[\"https://as.example\"]}");
        publish("https://as.example/.well-known/oauth-authorization-server", "{\"jwks_uri\":\"https://as.example/k\"}");
        publish("https://as.example/k", "{\"keys\":[" + RSA_JWK + "," + RSA_JWK + "]}");

        assertThat(discover("https://mcp.example/mcp",
                TestTokens.token("{\"alg\":\"RS256\"}", "{\"iss\":\"https://as.example\"}"))).hasSize(4);
        assertThat(fetched()).containsOnlyOnce("https://as.example/k");
    }

    @Test
    void ignoresNonHttpUrlsAndMalformedDocuments() {
        publish("https://mcp.example/.well-known/oauth-protected-resource/mcp",
                "{\"authorization_servers\":[\"file:///etc\",7,\"https://as.example\"]}");
        publish("https://as.example/.well-known/oauth-authorization-server", "{\"jwks_uri\":\"gopher://x\"}");
        publish("https://as.example/.well-known/openid-configuration", "{\"jwks_uri\":7}");

        assertThat(discover("https://mcp.example/mcp", TestTokens.token("{\"alg\":\"RS256\"}", "{}"))).isEmpty();
        assertThat(fetched()).noneMatch(url -> url.startsWith("file:") || url.startsWith("gopher:"));
    }

    @Test
    void limitsHowManyAuthorizationServersItFollows() {
        publish("https://mcp.example/.well-known/oauth-protected-resource/mcp",
                "{\"authorization_servers\":[\"https://a1.example\",\"https://a2.example\",\"https://a3.example\","
                        + "\"https://a4.example\"]}");

        discover("https://mcp.example/mcp", TestTokens.token("{\"alg\":\"RS256\"}", "{}"));

        assertThat(fetched()).noneMatch(url -> url.contains("a4.example"));
    }

    @Test
    void findsNothingWhenNoMetadataIsPublished() {
        assertThat(discover("http://mcp.example:8080/mcp", TestTokens.token("{\"alg\":\"RS256\"}", "{}"))).isEmpty();
        assertThat(fetched()).contains("http://mcp.example:8080/.well-known/oauth-protected-resource/mcp");
    }

    @Test
    void looksUpAuthorizationServerMetadataAtEverySharedWellKnownLocation() {
        publish("https://mcp.example/.well-known/oauth-protected-resource/mcp",
                "{\"authorization_servers\":[\"https://as.example/tenant\"]}");
        publish("https://as.example/tenant/.well-known/oauth-authorization-server",
                "{\"jwks_uri\":\"https://as.example/keys\"}");
        publish("https://as.example/keys", "{\"keys\":[" + RSA_JWK + "]}");

        assertThat(discover("https://mcp.example/mcp", TestTokens.token("{\"alg\":\"RS256\"}", "{}"))).hasSize(4);
    }

    @Test
    void stopsDiscoveringOnceTheServerAnswersTooManyRequests() {
        http.on("GET", "https://mcp.example/.well-known/oauth-protected-resource/mcp", status(429));
        publish("https://issuer.example/.well-known/oauth-authorization-server",
                "{\"jwks_uri\":\"https://issuer.example/jwks.json\"}");
        publish("https://issuer.example/jwks.json", "{\"keys\":[" + RSA_JWK + "]}");

        assertThat(discover("https://mcp.example/mcp",
                TestTokens.token("{\"alg\":\"RS256\"}", "{\"iss\":\"https://issuer.example\"}"))).isEmpty();
        assertThat(fetched()).containsExactly("https://mcp.example/.well-known/oauth-protected-resource/mcp");
    }

    @Test
    void findsTheKeyTheFastJwtBenignCveTargetPublishes() {
        String origin = "http://127.0.0.1:49152";
        publish(origin + "/.well-known/oauth-protected-resource/mcp",
                "{\"resource\":\"" + origin + "/mcp\",\"authorization_servers\":[\"" + origin + "\"]}");
        publish(origin + "/.well-known/oauth-authorization-server",
                "{\"issuer\":\"" + origin + "\",\"jwks_uri\":\"" + origin + "/jwks.json\"}");
        publish(origin + "/jwks.json", "{\"keys\":[" + RSA_JWK.replace("\"kid\":\"k1\"",
                "\"kid\":\"fixture-key-1\",\"use\":\"sig\",\"alg\":\"RS256\"") + "]}");

        List<PublicKeyMaterial> keys = discover(origin + "/mcp",
                TestTokens.token("{\"alg\":\"RS256\",\"kid\":\"fixture-key-1\"}", "{\"sub\":\"alice\"}"));

        assertThat(keys).isNotEmpty();
        assertThat(keys).allSatisfy(key -> assertThat(key.description())
                .contains("fixture-key-1", origin + "/jwks.json"));
    }
}
