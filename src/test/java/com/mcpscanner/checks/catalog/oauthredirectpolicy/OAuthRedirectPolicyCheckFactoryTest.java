package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.CheckServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthRedirectPolicyCheckFactoryTest {

    @Test
    void createsTheOAuthRedirectPolicyCheck() {
        assertThat(new OAuthRedirectPolicyCheckFactory().create(CheckServices.standalone()))
                .isInstanceOf(OAuthRedirectPolicyCheck.class);
    }

    @Test
    void isDiscoverableFromTheCatalogById() {
        assertThat(CheckCatalog.discover(CheckServices.standalone()).find("oauth-redirect-policy"))
                .hasValueSatisfying(check -> {
                    assertThat(check).isInstanceOf(OAuthRedirectPolicyCheck.class);
                    assertThat(check.descriptor().kind()).isEqualTo(CheckKind.HOST);
                });
    }
}
