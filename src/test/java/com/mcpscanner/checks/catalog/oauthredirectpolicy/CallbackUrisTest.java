package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackUrisTest {

    @Test
    void loopbackCallbacksShareHostAndPortButNotPath() {
        URI registered = CallbackUris.loopbackRegistered();
        URI unregistered = CallbackUris.loopbackUnregistered();

        assertThat(registered.toString()).startsWith("http://127.0.0.1:49151/scancheck-registered-");
        assertThat(unregistered.toString()).startsWith("http://127.0.0.1:49151/scancheck-unregistered-");
    }

    @Test
    void everyCallbackIsFresh() {
        assertThat(CallbackUris.loopbackRegistered()).isNotEqualTo(CallbackUris.loopbackRegistered());
        assertThat(CallbackUris.httpsControl()).isNotEqualTo(CallbackUris.httpsControl());
    }

    @Test
    void httpsControlIsOnTheClientHost() {
        assertThat(CallbackUris.httpsControl().toString()).startsWith("https://client.example.com/scancheck-control-");
    }

    @Test
    void httpDowngradeKeepsAuthorityAndPathAndDropsQuery() {
        URI downgrade = CallbackUris.httpDowngradeOf(URI.create("https://client.example.com:8443/cb?x=1"));

        assertThat(downgrade).isEqualTo(URI.create("http://client.example.com:8443/cb"));
    }

    @Test
    void traversalNormalisesToAFreshSiblingOfTheRegisteredPath() {
        URI registered = URI.create("https://client.example.com/cb");

        URI traversal = CallbackUris.traversalBeneath(registered);

        assertThat(traversal.toString()).startsWith("https://client.example.com/cb/../scancheck-bypass-");
        assertThat(traversal.normalize().toString()).startsWith("https://client.example.com/scancheck-bypass-");
    }

    @Test
    void wildcardMatchingHostSitsUnderThePatternAndUnmatchedHostDoesNot() {
        CallbackUris.WildcardCallback wildcard = CallbackUris.wildcard();
        String path = wildcard.pattern().substring(wildcard.pattern().indexOf("/scancheck-wildcard-"));

        assertThat(wildcard.pattern()).startsWith("https://*.client.example.com/scancheck-wildcard-");
        assertThat(wildcard.matchingHost().getHost()).endsWith(".client.example.com");
        assertThat(wildcard.unmatchedHost().getHost()).endsWith(".example.org");
        assertThat(wildcard.matchingHost().getPath()).isEqualTo(path);
        assertThat(wildcard.unmatchedHost().getPath()).isEqualTo(path);
    }

    @Test
    void wildcardUnmatchedPathSitsOnAMatchingHostOutsideThePatternPath() {
        CallbackUris.WildcardCallback wildcard = CallbackUris.wildcard();

        assertThat(wildcard.unmatchedPath().getHost()).endsWith(".client.example.com");
        assertThat(wildcard.unmatchedPath().getPath()).startsWith("/scancheck-unlisted-");
    }
}
