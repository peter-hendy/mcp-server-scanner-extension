package com.mcpscanner.auth.grant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClientTypeTest {

    @ParameterizedTest
    @CsvSource({"client_secret_basic", "client_secret_post", "CLIENT_SECRET_BASIC"})
    void registrationReturningASecretWithASecretMethodIsConfidential(String method) {
        assertThat(ClientType.ofRegistration("s3cret", method)).isEqualTo(ClientType.CONFIDENTIAL_REGISTERED);
    }

    @ParameterizedTest
    @CsvSource(value = {"none", "private_key_jwt", "client_secret_jwt", "tls_client_auth", "NULL"}, nullValues = "NULL")
    void registrationReturningASecretWithoutASecretMethodIsPublic(String method) {
        assertThat(ClientType.ofRegistration("s3cret", method)).isEqualTo(ClientType.PUBLIC);
    }

    @Test
    void registrationWithoutASecretIsPublicWhateverTheMethod() {
        assertThat(ClientType.ofRegistration(null, "client_secret_basic")).isEqualTo(ClientType.PUBLIC);
        assertThat(ClientType.ofRegistration(" ", "client_secret_basic")).isEqualTo(ClientType.PUBLIC);
    }

    @Test
    void configuredSecretIsConfidentialByConfiguration() {
        assertThat(ClientType.ofConfiguredSecret("s3cret")).isEqualTo(ClientType.CONFIDENTIAL_CONFIGURED);
        assertThat(ClientType.ofConfiguredSecret(null)).isEqualTo(ClientType.PUBLIC);
        assertThat(ClientType.ofConfiguredSecret("")).isEqualTo(ClientType.PUBLIC);
    }

    @Test
    void onlyTheConfidentialTypesAreConfidential() {
        assertThat(ClientType.PUBLIC.isConfidential()).isFalse();
        assertThat(ClientType.CONFIDENTIAL_CONFIGURED.isConfidential()).isTrue();
        assertThat(ClientType.CONFIDENTIAL_REGISTERED.isConfidential()).isTrue();
    }
}
