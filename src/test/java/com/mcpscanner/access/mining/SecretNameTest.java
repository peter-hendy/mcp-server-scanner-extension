package com.mcpscanner.access.mining;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecretNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"key", "KEY", "api_key", "apiKey", "apikey", "API-KEY", "private_key", "secret_key",
            "accessKey", "access-key", "signing_key", "encryptionKey", "accessToken", "password", "session_id",
            "cookie", "jwt", "openai_api_key", "stripeSecretKey", "aws-access-key", "gpgSigningKey"})
    void secretNames(String name) {
        assertThat(SecretName.matches(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"issue_key", "project_key", "issueKey", "keyword", "monkey", "id", "campaign_id"})
    void idNamesThatOnlyContainKey(String name) {
        assertThat(SecretName.matches(name)).isFalse();
    }
}
