package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.SharedCredentials;
import com.mcpscanner.ui.widgets.WarningBanner;

import java.util.List;
import java.util.stream.Collectors;

final class SharedCredentialsBanner extends WarningBanner {

    private static final String CONSEQUENCE =
            ": Check access and Scan do not compare users signed in as the same account.";

    private final SharedCredentials shared;

    SharedCredentialsBanner(SharedCredentials shared) {
        super("");
        this.shared = shared;
        setVisible(false);
    }

    void refresh(Identity identity) {
        List<Identity> sharing = shared.sharingWith(identity.id());
        setVisible(!sharing.isEmpty());
        setMessage("Same credentials as " + sharing.stream().map(Identity::name).collect(Collectors.joining(", "))
                + CONSEQUENCE);
    }
}
