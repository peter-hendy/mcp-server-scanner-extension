package com.mcpscanner.checks.catalog.originhostadmission;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TargetExposureTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void aLoopbackRequestWithoutCredentialsIsLocalAndAnonymous() {
        TargetExposure exposure = TargetExposure.of(AdmissionServers.toolsList(AdmissionServers.LOOPBACK_URL),
                new CredentialHeaders(NoAuthStrategy::new));

        assertThat(exposure).isEqualTo(new TargetExposure(true, false, false));
    }

    @Test
    void aPublicRequestCarryingABearerIsRemoteAndCredentialed() {
        TargetExposure exposure = TargetExposure.of(AdmissionServers.toolsList(AdmissionServers.PUBLIC_URL)
                .withAddedHeader("Authorization", "Bearer t"), new CredentialHeaders(NoAuthStrategy::new));

        assertThat(exposure).isEqualTo(new TargetExposure(false, true, true));
    }

    @Test
    void aRequestCarryingTheActiveStrategyHeadersIsCredentialed() {
        CredentialHeaders credentials = new CredentialHeaders(() -> new BearerTokenAuthStrategy("t"));

        assertThat(TargetExposure.of(credentials.withMissingAdded(
                AdmissionServers.toolsList(AdmissionServers.LOOPBACK_URL)), credentials).credentialed()).isTrue();
    }
}
