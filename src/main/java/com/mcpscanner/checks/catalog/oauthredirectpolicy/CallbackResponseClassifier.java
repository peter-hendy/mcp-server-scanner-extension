package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.shared.oauth.ProbeCallback;
import com.mcpscanner.checks.shared.oauth.RedirectStatus;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

final class CallbackResponseClassifier {

    private static final int MAX_LOCATION_LENGTH = 16_384;

    Delivery classify(AuthorizationOutcome outcome, URI callback, String state) {
        return parseRedirect(outcome)
                .flatMap(location -> ProbeCallback.response(callback, location, state))
                .map(response -> response.deliversCode() ? Delivery.CODE : Delivery.ERROR)
                .orElse(Delivery.REJECTED);
    }

    private static Optional<URI> parseRedirect(AuthorizationOutcome outcome) {
        String location = outcome.location();
        if (!RedirectStatus.isRedirect(outcome.status()) || location == null
                || location.length() > MAX_LOCATION_LENGTH) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URI(location));
        } catch (URISyntaxException invalid) {
            return Optional.empty();
        }
    }
}
