package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.grant.ClientType;
import com.mcpscanner.auth.grant.OAuthClient;
import com.mcpscanner.auth.grant.RefreshGrant;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshRequestFactoryTest {

    private final RefreshRequestFactory factory = new RefreshRequestFactory(URI.create("https://as.example/token"));

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void registeredConfidentialRefreshAuthenticatesWithBasic() {
        HttpRequest request = factory.refresh(grant(ClientType.CONFIDENTIAL_REGISTERED), "rt",
                ClientAuthentication.REGISTERED, Optional.empty());

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.url()).isEqualTo("https://as.example/token");
        assertThat(request.headerValue("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
        assertThat(request.headerValue("Authorization"))
                .isEqualTo(BasicCredentials.header("client 1", "s3cret:x"));
        assertThat(request.bodyToString()).isEqualTo(
                "grant_type=refresh_token&refresh_token=rt&resource=https%3A%2F%2Fmcp.example%2Fmcp");
    }

    @Test
    void omittedSecretSendsOnlyTheClientIdInTheBody() {
        HttpRequest request = factory.refresh(grant(ClientType.CONFIDENTIAL_REGISTERED), "rt",
                ClientAuthentication.OMITTED_SECRET, Optional.empty());

        assertThat(request.headerValue("Authorization")).isNull();
        assertThat(request.bodyToString()).endsWith("&client_id=client+1").doesNotContain("s3cret");
    }

    @Test
    void wrongSecretSendsBasicWithAFixedWrongSecret() {
        HttpRequest request = factory.refresh(grant(ClientType.CONFIDENTIAL_CONFIGURED), "rt",
                ClientAuthentication.WRONG_SECRET, Optional.empty());

        assertThat(request.headerValue("Authorization"))
                .isEqualTo(BasicCredentials.header("client 1", RefreshRequestFactory.WRONG_SECRET));
    }

    @Test
    void publicClientsAlwaysIdentifyInTheBody() {
        HttpRequest request = factory.refresh(grant(ClientType.PUBLIC), "rt", ClientAuthentication.REGISTERED,
                Optional.of(Set.of("b", "a")));

        assertThat(request.headerValue("Authorization")).isNull();
        assertThat(request.bodyToString()).contains("&scope=a+b&client_id=client+1");
    }

    @Test
    void basicCredentialsFormEncodeBothParts() {
        assertThat(BasicCredentials.header("a b", "c:d"))
                .isEqualTo("Basic " + java.util.Base64.getEncoder().encodeToString("a+b:c%3Ad".getBytes()));
    }

    private static RefreshGrant grant(ClientType type) {
        return new RefreshGrant(URI.create("https://as.example"), Optional.of(URI.create("https://mcp.example/mcp")),
                new OAuthClient("client 1", Optional.of("s3cret:x"), type), "rt", List.of());
    }
}
