package com.mcpscanner.ui;

record OptionalGrantInput(String issuer, String clientId, char[] clientSecret, String scopes,
                          String authenticationMethod, String signerId, String identityProviderId) {

    boolean hasClientSecret() {
        return clientSecret.length > 0;
    }
}
