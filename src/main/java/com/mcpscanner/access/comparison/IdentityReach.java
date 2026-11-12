package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.crossidentity.OperationReply;
import com.mcpscanner.checks.api.crossidentity.ProtocolVariant;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Optional;

record IdentityReach(OperationReply finalReply, Optional<HttpRequest> acceptedRequest, ProtocolVariant variant) {

    static IdentityReach reached(OperationReply reply, HttpRequest request, ProtocolVariant variant) {
        return new IdentityReach(reply, Optional.of(request), variant);
    }

    static IdentityReach unreached(OperationReply reply, ProtocolVariant variant) {
        return new IdentityReach(reply, Optional.empty(), variant);
    }

    boolean ownerResultReached() {
        return acceptedRequest.isPresent();
    }

    IdentityReach withdrawn() {
        return unreached(finalReply, variant);
    }
}
