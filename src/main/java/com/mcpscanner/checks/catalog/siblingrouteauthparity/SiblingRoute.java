package com.mcpscanner.checks.catalog.siblingrouteauthparity;

import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.auth.RequestMutation;

import java.util.Optional;

record SiblingRoute(AuthVariant variant, FindingKind kind, Optional<RequestMutation> sweptElsewhereControl) {

    SiblingRoute(AuthVariant variant, FindingKind kind) {
        this(variant, kind, Optional.empty());
    }

    boolean sweptElsewhere() {
        return sweptElsewhereControl.isPresent();
    }
}
