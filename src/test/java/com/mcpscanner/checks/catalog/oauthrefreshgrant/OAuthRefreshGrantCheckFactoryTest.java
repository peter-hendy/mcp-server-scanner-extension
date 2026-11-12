package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthRefreshGrantCheckFactoryTest {

    @Test
    void theServiceFileRegistersTheCheckUnderItsId() {
        assertThat(CheckCatalog.discover(CheckServices.standalone()).find("oauth-refresh-grant"))
                .get().isInstanceOf(OAuthRefreshGrantCheck.class);
    }
}
