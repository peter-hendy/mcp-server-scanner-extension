package com.mcpscanner.auth.grants;

import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;

public interface ClientAuthenticationProvider {
    String id();
    ClientCredentialsAuthentication authentication();
}
