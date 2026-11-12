package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

enum ReflectedField {
    CLIENT_NAME("OAuth authorization flow page renders client_name as HTML structure",
            "the client_name registered through dynamic client registration"),
    STATE("OAuth authorization flow page renders state as HTML structure",
            "the state parameter of the authorization request");

    private final String issueName;
    private final String origin;

    ReflectedField(String issueName, String origin) {
        this.issueName = issueName;
        this.origin = origin;
    }

    String issueName() {
        return issueName;
    }

    String origin() {
        return origin;
    }
}
