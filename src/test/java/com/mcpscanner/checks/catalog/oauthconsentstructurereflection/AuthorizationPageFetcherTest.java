package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.shared.oauth.OAuthHttp;
import com.mcpscanner.checks.shared.oauth.ScriptedHttp;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.html;
import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.redirect;
import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPageFetcherTest {

    private static final String AUTHORIZE = "https://as.example/authorize";

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void directPageIsReturnedWithItsExchange() {
        ScriptedHttp server = new ScriptedHttp().on("GET", AUTHORIZE, html("<h1>Consent</h1>"));

        Optional<AuthorizationPage> page = fetch(server, AUTHORIZE + "?client_id=c");

        assertThat(page).map(AuthorizationPage::url).contains(URI.create(AUTHORIZE + "?client_id=c"));
        assertThat(page.orElseThrow().exchanges()).hasSize(1);
        assertThat(server.sent().get(0).headerValue("Accept")).startsWith("text/html");
    }

    @Test
    void sameOriginRedirectsAreFollowedToTheConsentPage() {
        ScriptedHttp server = new ScriptedHttp()
                .on("GET", AUTHORIZE, redirect("/consent?tx=1"))
                .on("GET", "https://as.example/consent", html("<h1>Consent</h1>"));

        Optional<AuthorizationPage> page = fetch(server, AUTHORIZE);

        assertThat(page).map(AuthorizationPage::url).contains(URI.create("https://as.example/consent?tx=1"));
        assertThat(page.orElseThrow().exchanges()).hasSize(2);
    }

    @Test
    void crossOriginRedirectStopsAtTheRedirect() {
        ScriptedHttp server = new ScriptedHttp().on("GET", AUTHORIZE, redirect("https://idp.example/login"));

        Optional<AuthorizationPage> page = fetch(server, AUTHORIZE);

        assertThat(page.orElseThrow().finalExchange().response().statusCode()).isEqualTo((short) 302);
        assertThat(server.sent()).hasSize(1);
    }

    @Test
    void redirectChainsAreBounded() {
        ScriptedHttp server = new ScriptedHttp().on("GET", AUTHORIZE, redirect(AUTHORIZE));

        Optional<AuthorizationPage> page = fetch(server, AUTHORIZE);

        assertThat(page.orElseThrow().exchanges()).hasSize(AuthorizationPageFetcher.MAX_REDIRECTS + 1);
    }

    @Test
    void anUnansweredHopMeansNoPage() {
        ScriptedHttp server = new ScriptedHttp()
                .on("GET", AUTHORIZE, redirect("/consent"))
                .on("GET", "https://as.example/consent", ScriptedHttp.NO_RESPONSE);

        assertThat(fetch(server, AUTHORIZE)).isEmpty();
    }

    private static Optional<AuthorizationPage> fetch(ScriptedHttp server, String url) {
        return new AuthorizationPageFetcher(new OAuthHttp(server.http())).fetch(URI.create(url));
    }
}
