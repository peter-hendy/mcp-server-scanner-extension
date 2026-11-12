package com.mcpscanner.auth.oauth;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeGrantTest {

    @Test
    void namesGrantedAndWithheldScopesWhenTheGrantIsNarrower() {
        ScopeGrant grant = new ScopeGrant(Set.of("read", "write"), Set.of("read"));

        assertThat(grant.withheld()).containsExactly("write");
        assertThat(grant.withheldWarning()).hasValue("Authorization server granted read but withheld write; "
                + "tools needing write will fail. Reconnect as a user allowed write to test them.");
    }

    @Test
    void saysNoScopesWhenNothingWasGranted() {
        ScopeGrant grant = new ScopeGrant(Set.of("write", "admin"), Set.of());

        assertThat(grant.withheldWarning()).hasValue("Authorization server granted no scopes but withheld admin write; "
                + "tools needing admin write will fail. Reconnect as a user allowed admin write to test them.");
    }

    @Test
    void staysQuietWhenEveryRequestedScopeWasGranted() {
        assertThat(new ScopeGrant(Set.of("read"), Set.of("read", "profile")).withheldWarning()).isEmpty();
        assertThat(new ScopeGrant(Set.of(), Set.of("read")).withheldWarning()).isEmpty();
    }
}
