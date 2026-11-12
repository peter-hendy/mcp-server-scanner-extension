package com.mcpscanner.cvetargets.manifest;

import com.mcpscanner.auth.AuthStrategy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeaderAddingStrategyTest {

    @Test
    void addsHeadersWithoutHidingTheCredentialOrItsRefresh() {
        AuthStrategy credential = mock(AuthStrategy.class);
        when(credential.headers()).thenReturn(Map.of("Authorization", "Bearer fresh"));
        when(credential.supportsRefresh()).thenReturn(true);
        when(credential.refresh()).thenReturn(true);

        HeaderAddingStrategy strategy = new HeaderAddingStrategy(credential, Map.of("X-Upstream-Url", "http://u.test"));

        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer fresh")
                .containsEntry("X-Upstream-Url", "http://u.test");
        assertThat(strategy.supportsRefresh()).isTrue();
        assertThat(strategy.refresh()).isTrue();
    }
}
