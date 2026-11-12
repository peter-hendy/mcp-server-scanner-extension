package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

final class PublicKeyAsHmacSecretForgery implements ForgeryStrategy {

    @Override
    public List<ForgeryAttempt> attemptsFor(ForgeryContext context) {
        if (!context.captured().asymmetricallySigned()) {
            return List.of();
        }
        ObjectNode header = ForgedHeader.from(context.captured(), "HS256");
        return context.publicKeys().stream()
                .map(key -> ForgeryAttempt.controlledBy(
                        new HmacSignature("an HS256 token whose HMAC secret is " + key.description(), header,
                                key.bytes()),
                        new HmacSignature(HmacSignature.RANDOM_SECRET_DESCRIPTION, header,
                                RandomSecret.bytes())))
                .toList();
    }
}
