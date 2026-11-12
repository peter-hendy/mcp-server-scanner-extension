package com.mcpscanner.checks.catalog.oauthrefreshgrant;

enum ClientAuthentication {
    REGISTERED("the registered client credentials"),
    OMITTED_SECRET("the client_id only, with no client secret"),
    WRONG_SECRET("the client_id with a wrong client secret");

    private final String description;

    ClientAuthentication(String description) {
        this.description = description;
    }

    String description() {
        return description;
    }
}
