package com.mcpscanner.mcp;

import burp.api.montoya.http.HttpService;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpOriginTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void ofUriFillsInDefaultHttpsPort() {
        assertThat(HttpOrigin.of(URI.create("https://Example.COM/mcp?x=1")))
                .isEqualTo(new HttpOrigin("https", "example.com", 443));
    }

    @Test
    void ofUriFillsInDefaultHttpPort() {
        assertThat(HttpOrigin.of(URI.create("http://example.com/mcp")).port()).isEqualTo(80);
    }

    @Test
    void ofUriKeepsExplicitPort() {
        assertThat(HttpOrigin.of(URI.create("http://example.com:8080/")).port()).isEqualTo(8080);
    }

    @Test
    void ofUriRejectsRelativeUri() {
        assertThatThrownBy(() -> HttpOrigin.of(URI.create("/message")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseReturnsEmptyForRelativeOrMalformedUri() {
        assertThat(HttpOrigin.parse(URI.create("/message"))).isEmpty();
        assertThat(HttpOrigin.parse((URI) null)).isEmpty();
        assertThat(HttpOrigin.parse("not a uri")).isEmpty();
    }

    @Test
    void parseStringReturnsOrigin() {
        assertThat(HttpOrigin.parse("https://example.com:8443/mcp"))
                .isEqualTo(Optional.of(new HttpOrigin("https", "example.com", 8443)));
    }

    @Test
    void ofHttpServiceUsesSecureFlagForScheme() {
        HttpService service = HttpService.httpService("example.com", 8443, true);

        assertThat(HttpOrigin.of(service)).isEqualTo(new HttpOrigin("https", "example.com", 8443));
    }

    @Test
    void sameOriginTreatsImplicitAndExplicitDefaultPortAsEqual() {
        assertThat(HttpOrigin.sameOrigin(URI.create("https://example.com/a"), URI.create("https://EXAMPLE.com:443/b")))
                .isTrue();
    }

    @Test
    void sameOriginRejectsSchemeDowngrade() {
        assertThat(HttpOrigin.sameOrigin(URI.create("https://example.com/sse"), URI.create("http://example.com/message")))
                .isFalse();
    }

    @Test
    void sameOriginRejectsPortChange() {
        assertThat(HttpOrigin.sameOrigin(URI.create("http://example.com/sse"), URI.create("http://example.com:9000/m")))
                .isFalse();
    }

    @Test
    void sameOriginRejectsRelativeUri() {
        assertThat(HttpOrigin.sameOrigin(URI.create("http://example.com/sse"), URI.create("/m"))).isFalse();
    }

    @Test
    void hostHeaderOmitsDefaultPort() {
        assertThat(new HttpOrigin("https", "example.com", 443).hostHeader()).isEqualTo("example.com");
        assertThat(new HttpOrigin("http", "example.com", 80).hostHeader()).isEqualTo("example.com");
    }

    @Test
    void hostHeaderIncludesNonDefaultPort() {
        assertThat(new HttpOrigin("https", "example.com", 80).hostHeader()).isEqualTo("example.com:80");
    }

    @Test
    void baseUrlCombinesSchemeAndHostHeader() {
        assertThat(new HttpOrigin("http", "example.com", 8080).baseUrl()).isEqualTo("http://example.com:8080");
    }

    @Test
    void toHttpServiceRoundTrips() {
        HttpService service = new HttpOrigin("https", "example.com", 8443).toHttpService();

        assertThat(service.host()).isEqualTo("example.com");
        assertThat(service.port()).isEqualTo(8443);
        assertThat(service.secure()).isTrue();
    }

    @Test
    void toStringIsBaseUrlForLogging() {
        assertThat(new HttpOrigin("https", "example.com", 443).toString()).isEqualTo("https://example.com");
    }

    @Test
    void bracketsAnIpv6HostOnce() {
        assertThat(new HttpOrigin("http", "::1", 8080).baseUrl()).isEqualTo("http://[::1]:8080");
        assertThat(new HttpOrigin("https", "::1", 443).hostHeader()).isEqualTo("[::1]");
        assertThat(HttpOrigin.parse("http://[::1]:8080/mcp").orElseThrow().baseUrl()).isEqualTo("http://[::1]:8080");
    }
}
