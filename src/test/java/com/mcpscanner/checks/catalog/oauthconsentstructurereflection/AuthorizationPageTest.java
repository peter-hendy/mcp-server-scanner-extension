package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.html;
import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.json;
import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPageTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void htmlPageIsParsed() {
        assertThat(page(html("<mcpscan-el-x></mcpscan-el-x>")).document().containsActiveElement("mcpscan-el-x"))
                .isTrue();
    }

    @Test
    void nonHtmlOrRedirectBodiesAreNotRendered() {
        assertThat(page(json(400, "{\"e\":\"<mcpscan-el-x>\"}")).document().containsActiveElement("mcpscan-el-x"))
                .isFalse();
        assertThat(page(html(302, "<mcpscan-el-x></mcpscan-el-x>", "Location: /x")).document()
                .containsActiveElement("mcpscan-el-x")).isFalse();
    }

    @Test
    void enforcedPoliciesCombineHeadersAndMetaButNotReportOnly() {
        AuthorizationPage page = page(html(
                "<head><meta http-equiv=\"Content-Security-Policy\" content=\"img-src 'none'\"></head>",
                "Content-Security-Policy: script-src 'self'",
                "Content-Security-Policy-Report-Only: script-src 'none'"));

        assertThat(page.enforcedContentSecurityPolicies()).containsExactly("script-src 'self'", "img-src 'none'");
    }

    @Test
    void finalExchangeIsTheLastHop() {
        HttpRequestResponse first = exchange("HTTP/1.1 302 Found\r\nLocation: /b\r\n\r\n");
        HttpRequestResponse last = exchange(html("ok"));

        assertThat(new AuthorizationPage(URI.create("https://as.example/b"), List.of(first, last)).finalExchange())
                .isSameAs(last);
    }

    private static AuthorizationPage page(String raw) {
        return new AuthorizationPage(URI.create("https://as.example/a"), List.of(exchange(raw)));
    }

    private static HttpRequestResponse exchange(String raw) {
        return HttpRequestResponse.httpRequestResponse(HttpRequest.httpRequestFromUrl("https://as.example/a"),
                HttpResponse.httpResponse(raw));
    }
}
