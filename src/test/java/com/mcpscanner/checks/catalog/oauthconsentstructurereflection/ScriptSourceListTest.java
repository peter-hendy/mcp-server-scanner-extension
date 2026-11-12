package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptSourceListTest {

    @Test
    void unsafeInlineAllowsInlineScript() {
        assertThat(ScriptSourceList.parse("'self'  'UNSAFE-INLINE'").allowsInlineScript()).isTrue();
    }

    @Test
    void nonceHashOrStrictDynamicCancelsUnsafeInline() {
        assertThat(ScriptSourceList.parse("'unsafe-inline' 'nonce-x'").allowsInlineScript()).isFalse();
        assertThat(ScriptSourceList.parse("'unsafe-inline' 'sha384-x'").allowsInlineScript()).isFalse();
        assertThat(ScriptSourceList.parse("'unsafe-inline' 'strict-dynamic'").allowsInlineScript()).isFalse();
    }

    @Test
    void wildcardOrSchemeSourceAllowsArbitraryHostsUnlessStrictDynamic() {
        assertThat(ScriptSourceList.parse("https:").allowsArbitraryHostScript()).isTrue();
        assertThat(ScriptSourceList.parse("*").allowsArbitraryHostScript()).isTrue();
        assertThat(ScriptSourceList.parse("https: 'strict-dynamic'").allowsArbitraryHostScript()).isFalse();
        assertThat(ScriptSourceList.parse("https://cdn.example.com 'self'").allowsArbitraryHostScript()).isFalse();
        assertThat(ScriptSourceList.parse("https://*.example.com").allowsArbitraryHostScript()).isTrue();
    }

    @Test
    void hostSourcesAreListedHostsAndKeywordsAreNot() {
        assertThat(ScriptSourceList.parse("'self' cdn.example.com").allowsListedHosts()).isTrue();
        assertThat(ScriptSourceList.parse("'self' 'nonce-x'").allowsListedHosts()).isFalse();
        assertThat(ScriptSourceList.parse("cdn.example.com 'strict-dynamic'").allowsListedHosts()).isFalse();
    }

    @Test
    void onlySameOriginAcceptsSelfAndNoneOnly() {
        assertThat(ScriptSourceList.parse("'none'").onlySameOrigin()).isTrue();
        assertThat(ScriptSourceList.parse("'self'").onlySameOrigin()).isTrue();
        assertThat(ScriptSourceList.parse("'self' https:").onlySameOrigin()).isFalse();
        assertThat(ScriptSourceList.parse("").onlySameOrigin()).isFalse();
    }

    @Test
    void emptyListAllowsNothing() {
        ScriptSourceList none = ScriptSourceList.parse("");

        assertThat(none.allowsInlineScript()).isFalse();
        assertThat(none.allowsArbitraryHostScript()).isFalse();
    }
}
