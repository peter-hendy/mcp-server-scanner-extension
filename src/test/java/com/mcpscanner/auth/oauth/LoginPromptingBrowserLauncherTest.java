package com.mcpscanner.auth.oauth;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoginPromptingBrowserLauncherTest {

    private final List<URI> opened = new ArrayList<>();
    private final BrowserLauncher launcher = BrowserLauncher.promptingLogin(opened::add);

    @Test
    void asksTheAuthorizationServerForAFreshLogin() {
        launcher.open(URI.create("https://as.example/authorize?client_id=c&state=s"));

        assertThat(opened).containsExactly(URI.create("https://as.example/authorize?client_id=c&state=s&prompt=login"));
    }

    @Test
    void replacesAnExistingPromptParameter() {
        launcher.open(URI.create("https://as.example/authorize?prompt=none&state=s"));

        assertThat(opened).containsExactly(URI.create("https://as.example/authorize?state=s&prompt=login"));
    }

    @Test
    void addsTheQueryWhenThereIsNone() {
        launcher.open(URI.create("https://as.example/authorize"));

        assertThat(opened).containsExactly(URI.create("https://as.example/authorize?prompt=login"));
    }

    @Test
    void addsLoginToAnExistingPromptList() {
        launcher.open(URI.create("https://as.example/authorize?prompt=consent&state=s"));

        assertThat(opened).containsExactly(URI.create("https://as.example/authorize?state=s&prompt=consent%20login"));
    }

    @Test
    void doesNotRepeatLoginAlreadyInThePromptList() {
        launcher.open(URI.create("https://as.example/authorize?prompt=login%20consent"));

        assertThat(opened).containsExactly(URI.create("https://as.example/authorize?prompt=login%20consent"));
    }

    @Test
    void putsThePromptInTheQueryAndKeepsTheFragment() {
        launcher.open(URI.create("https://as.example/authorize?state=s#section"));

        assertThat(opened).containsExactly(URI.create("https://as.example/authorize?state=s&prompt=login#section"));
    }

    @Test
    void addsAQueryBeforeTheFragment() {
        launcher.open(URI.create("https://as.example/authorize#section"));

        assertThat(opened).containsExactly(URI.create("https://as.example/authorize?prompt=login#section"));
    }
}
