package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;

public final class OAuthRedirectPolicyCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new OAuthRedirectPolicyCheck(services.eventLog(), services.authStrategy());
    }
}
