package com.mcpscanner.checks.catalog.siblingrouteauthparity;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.auth.CredentialStripper;
import com.mcpscanner.checks.shared.auth.RequestMutation;
import com.mcpscanner.checks.shared.auth.VariantFamily;

final class AdvertisedMessageRoute {

    private final CredentialStripper stripper;

    AdvertisedMessageRoute(CredentialStripper stripper) {
        this.stripper = stripper;
    }

    SiblingRoute routeFor(HttpRequest seed) {
        return new SiblingRoute(AuthVariant.uncontrolled(VariantFamily.SESSION, "advertised.sse-message-route",
                new RequestMutation(seed.pathWithoutQuery(), stripper::applyKeepingLiveSession)),
                FindingKind.ADVERTISED_MESSAGE_ROUTE);
    }
}
