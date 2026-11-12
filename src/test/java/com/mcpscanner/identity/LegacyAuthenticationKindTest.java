package com.mcpscanner.identity;

import com.mcpscanner.config.AuthTypeNames;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyAuthenticationKindTest {

    @ParameterizedTest
    @CsvSource({
            AuthTypeNames.BEARER + ", BEARER",
            AuthTypeNames.CUSTOM_HEADERS + ", CUSTOM_HEADERS",
            AuthTypeNames.OAUTH + ", OAUTH",
            AuthTypeNames.NONE + ", NONE",
            AuthTypeNames.AUTO + ", NONE",
            "something else, NONE"
    })
    void savedAuthenticationTypesMapOntoUserAuthKinds(String authType, IdentityAuthKind expected) {
        assertThat(LegacyAuthenticationKind.of(authType)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    void nothingSavedSendsNoCredentials(String authType) {
        assertThat(LegacyAuthenticationKind.of(authType)).isEqualTo(IdentityAuthKind.NONE);
    }
}
