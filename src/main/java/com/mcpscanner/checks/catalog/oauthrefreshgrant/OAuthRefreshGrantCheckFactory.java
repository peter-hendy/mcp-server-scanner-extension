package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;

public final class OAuthRefreshGrantCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new OAuthRefreshGrantCheck(services.authStrategy(), services.eventLog());
    }
}
