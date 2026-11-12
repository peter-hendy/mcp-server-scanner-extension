package com.mcpscanner.ui;

import com.mcpscanner.auth.grants.OptionalGrantSettings;

public enum OAuthGrant {
    AUTHORIZATION_CODE("Authorization code", null),
    CLIENT_CREDENTIALS("Client credentials", OptionalGrantSettings.Profile.CLIENT_CREDENTIALS),
    ENTERPRISE_MANAGED("Enterprise-managed", OptionalGrantSettings.Profile.ENTERPRISE_MANAGED);

    private final String label;
    private final OptionalGrantSettings.Profile profile;

    OAuthGrant(String label, OptionalGrantSettings.Profile profile) {
        this.label = label;
        this.profile = profile;
    }

    public String label() {
        return label;
    }

    public static OAuthGrant forProfile(OptionalGrantSettings.Profile profile) {
        for (OAuthGrant grant : values()) {
            if (grant.profile == profile) {
                return grant;
            }
        }
        throw new IllegalArgumentException("Unknown grant profile");
    }
}
