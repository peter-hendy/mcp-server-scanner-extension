package com.mcpscanner.auth.grants;

import com.mcpscanner.core.oauth.EnterpriseIdentityProvider;

public interface EnterpriseIdentityProviderRegistration {
    String id();
    EnterpriseIdentityProvider provider();
}
